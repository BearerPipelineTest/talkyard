/**
 * Copyright (c) 2022 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package talkyard.server.webhooks

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.dao.{MemCacheKey, MemCacheValueIgnoreVersion, SiteDao}
import talkyard.server.parser.EventAndJson
import talkyard.server.parser.EventsParSer
import talkyard.{server => tys}

import play.api.libs.ws._
import play.api.libs.json.Json
import scala.concurrent.Future
import scala.concurrent.duration._
import org.scalactic.{Good, Or, Bad}
import scala.util.{Success, Failure}


case class PreparedWebhookReq(
  req: WebhookReqOut,
  nextConseqEvent: Event)


trait WebhooksSiteDaoMixin {
  self: SiteDao =>

  def upsertWebhook(webhook: Webhook): U = {
    writeTx { (tx, _) =>
      tx.upsertWebhook(webhook)
    }
  }

  def sendPendingWebhookReqs(webhooks: ImmSeq[Webhook]): U = {
    webhooks foreach { w =>
      sendReqsForOneWebhook(w)
    }
  }

  def sendReqsForOneWebhook(webhook: Webhook): U = {
    val (events: ImmSeq[Event], now_) = readTx { tx =>
      // Maybe break out to own fn?  Dao.loadEvents()?  [load_events_fn]
      val logEntries = tx.loadEventsFromAuditLog(newerOrAt = Some(webhook.sentUpToWhen),
            newerThanEventId = webhook.sentUpToEventId,
            limit = webhook.sendMaxEventsPerReq.getOrElse(1))
      (logEntries.flatMap(Event.fromAuditLogItem), tx.now)
    }

    COULD_OPTIMIZE // Mark webhooks as done_for_now_c, and mark dirty on new and edited posts.
    if (events.isEmpty)
      return

    val reqToSend = generateWebhookRequest(webhook, events) getOrIfBad { problem =>
      val webhookAfter = webhook.copy(
            failedHow = Some(SendFailedHow.BadConfig),
            failedSince = Some(now_),
            errMsgOrResp = Some(problem),
            brokenReason = Some(WebhookBrokenReason.BadConfig))(IfBadDie)
      upsertWebhook(webhookAfter)
      return
    }

    insertWebhookReqOut(reqToSend)


    sendWebhookRequest(reqToSend) onComplete {
      case Success(reqOut: WebhookReqOut) =>
        // The request might have failed, but at least we tried.
        updWebhookAndReq(webhook, reqOut, events, now_)
      case Failure(ex: Throwable) =>
        logger.error("Error sending webhook [TyEWHSNDUNKERR]", ex)
    }
  }


  private def updWebhookAndReq(webhook: Webhook, reqOut: WebhookReqOut,
          events: ImmSeq[Event], now_ : When): U = {

    // The event ids should be sequential — remember the highest sent.
    val latestByTime = events.maxBy(_.when.millis)
    val latestByEventId = events.maxBy(_.id)
    if (latestByTime.id != latestByEventId.id) {
      // Hmm. But what if 2 or more events, same timestamp?
      warnDevDie("TyELATESTEVENT", "The most recent event by time, is different from " +
            s"by event id. By time: ${latestByTime}, by event id: ${latestByEventId}")
    }

    val webhookAfter = {
      if (reqOut.failedHow.isEmpty) {
        webhook.copyAsWorking(
              sentUpToWhen = latestByTime.when,
              sentUpToEventId = Some(latestByEventId.id),
              numPendingMaybe = None, // unknown
              doneForNow = None)      // unknown
      }
      else {
        val defaultRetrySecs = 3600 * 24 * 3  // 3 days, same as Stripe [add_whk_conf]
        webhook.copyWithFailure(reqOut, now = now_, retryMaxSecs = defaultRetrySecs)
      }
    }

    upsertWebhook(webhookAfter)
    updateWebhookReqOut(reqOut)
  }


  private def generateWebhookRequest(webhook: Webhook, events: ImmSeq[Event])
        : WebhookReqOut Or ErrMsg = {
    dieIf(events.isEmpty, "TyE502MREDL6", "No events to send")

    val runAsUser = webhook.runAsId match {
      case None =>
        // Hmm. Maybe None should mean "run as a stranger" ?
        // getTheUser(SysbotUserId)
        None
      case Some(id) =>
        Some(getUser(id) getOrElse {
          val errMsg = s"Webhook run-as user not found, user id: $id [TyE0MWUX46MW]"
          logger.warn(errMsg)
          return Bad(errMsg)
        })
    }

    // Site origin.  Dupl code [603RKDJL5]
    val siteIdsOrigins = theSiteIdsOrigins()
    val avatarUrlPrefix =
          siteIdsOrigins.uploadsOrigin +
            talkyard.server.UploadsUrlBasePath + siteIdsOrigins.pubId + '/'

    val eventsJsonList: ImmSeq[EventAndJson] = EventsParSer.makeEventsListJson(
          events, dao = this, reqer = runAsUser, avatarUrlPrefix)

    val webhookReqBodyJson = Json.obj(
          "origin" -> siteIdsOrigins.siteOrigin,
          "events" -> eventsJsonList.map(_.json))

    val webhookSent = WebhookReqOut(
          webhookId = webhook.webhookId,
          sentAt = now(),
          sentToUrl = webhook.sendToUrl,
          sentByAppVer = generatedcode.BuildInfo.dockerTag,  // or  .version?
          sentFormatV = 1,
          sentEventTypes = eventsJsonList.map(ej => ej.event.eventType).toSet,
          //sentEventSubTypes =
          sentEventIds = eventsJsonList.map(_.event.id).toSet,
          sentJson = webhookReqBodyJson,
          sentHeaders = None,

          // We don't know, yet:
          failedAt = None,
          failedHow = None,
          errMsg = None,
          respAt = None,
          respStatus = None,
          respBody = None)

    Good(webhookSent)
  }


  private def insertWebhookReqOut(reqOut: WebhookReqOut): U = {
    writeTx { (tx, _) =>
      tx.insertWebhookReqOut(reqOut)
    }
  }


  private def updateWebhookReqOut(reqOut: WebhookReqOut): U = {
    writeTx { (tx, _) =>
      tx.updateWebhookReqOut(reqOut)
    }
  }


  private def sendWebhookRequest(reqOut: WebhookReqOut): Future[WebhookReqOut] = {
    val jsonSt: St = reqOut.sentJson.toString()
    val wsClient: WSClient = globals.wsClient
    val request: WSRequest =
          wsClient.url(reqOut.sentToUrl).withHttpHeaders(
              play.api.http.HeaderNames.CONTENT_TYPE -> play.api.http.ContentTypes.JSON,
              //play.api.http.HeaderNames.USER_AGENT -> UserAgent,
              play.api.http.HeaderNames.CONTENT_LENGTH -> jsonSt.length.toString)
              .withRequestTimeout(20.seconds)
              // .withConnectionTimeout(2.seconds)  // ?

    request.post(jsonSt).map({ response: WSResponse =>
      // Now we're in a different thread. Be careful so as not to cause db serialization
      // errors via lock conflicts ... hmm but how?
      try {
        var webhookReqAfter = reqOut.copy(
              respAt = Some(now()),
              respStatus = Some(response.status),
              respBody = Some(response.body),
              respHeaders = Some(tys.http.headersToJsonMultiMap(response.headers)))

        response.status match {
          case 200 | 201 =>
            logger.info(s"Got status ${response.status} from webhook req")
          case badStatus =>
// [retry_webhook] ?
            logger.warn(s"Got status $badStatus from webhook req")
            webhookReqAfter = webhookReqAfter.copy(
                  failedAt  = Some(now()),
                  failedHow = Some(SendFailedHow.ErrorResponseStatusCode))
        }
        webhookReqAfter
      }
      catch {
        case ex: Exception =>
          // This'd be a bug in Talkyard? Then don't retry the webhook.
// [mark_webhook_broken] ?
          logger.warn(s"Error handling webhook response [TyEPWHK1]", ex)
          reqOut.copy(
                failedAt  = Some(now()),
                failedHow = Some(SendFailedHow.TalkyardBug),
                errMsg = Some(ex.getMessage))
      }
    })(globals.executionContext)
      .recover({
        case ex: Exception =>
// [retry_webhook] ?
// [mark_webhook_broken] ?
          val failedHow = ex match {
            case _: scala.concurrent.TimeoutException => SendFailedHow.RequestTimedOut
            // Unsure precisely which of these are thrown:  (annoying! Would have
            // been better if Play's API returned an Ok Or ErrorEnum-plus-message?)
            case _: io.netty.channel.ConnectTimeoutException => SendFailedHow.CouldntConnect
            case _: java.net.ConnectException => SendFailedHow.CouldntConnect
            case _ => SendFailedHow.OtherException
          }
          logger.warn(s"Error sending webhook [TyEPWHK2]", ex)
          reqOut.copy(
                failedAt  = Some(now()),
                failedHow = Some(failedHow),
                errMsg = Some(ex.getMessage))
      })(globals.executionContext)
  }

}
