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
import debiki.EdHttp.urlDecodeCookie
import debiki.dao.{MemCacheKey, MemCacheValueIgnoreVersion, SiteDao}
import talkyard.server.dao.StaleStuff
import talkyard.server.parser
import talkyard.server.parser.EventAndJson

import org.apache.commons.codec.{binary => acb}
import play.api.libs.ws._
import scala.concurrent.Future
import scala.concurrent.duration._
import org.scalactic.{Good, Or, Bad}


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
    val events: ImmSeq[Event] = readTx { tx =>
      // Maybe break out to own fn?  Dao.loadEvents()?  [load_events_fn]
      val logEntries = tx.loadEventsFromAuditLog(newerOrAt = Some(webhook.sentUpToWhen),
            newerThanEventId = webhook.sentUpToEventId, limit = webhook.sendBatchSize)
      logEntries.flatMap(Event.fromAuditLogItem)
    }

    val reqToSend = prepareWebhookRequest(webhook, events) getOrIfBad { problem =>
      val webhookAfter = webhook.copy(brokenReason = Some(problem))(IfBadDie)
      upsertWebhook(webhookAfter)
      return
    }

    sendWebhookRequest(reqToSend)

    // The event ids should be sequential — remember the highest sent.
    val latestByTime = events.maxBy(_.when.millis)
    val latestByEventId = events.maxBy(_.id)
    if (latestByTime.id != latestByEventId.id) {
      // Hmm1 But what if 2 events, same timestamp?
      warnDevDie("TyELATESTEVENT", "The most recent event by time, is different from " +
            s"by event id. By time: ${latestByTime}, by event id: ${latestByEventId}")
    }

    val webhookAfter = webhook.copy(
          sentUpToWhen = latestByTime.when,
          sentUpToEventId = Some(latestByEventId.id))(IfBadDie)

    upsertWebhook(webhookAfter)
  }


  private def prepareWebhookRequest(webhook: Webhook, events: ImmSeq[Event])
        : WebhookSent Or ErrMsg = {
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

    val eventsJson: ImmSeq[EventAndJson] = talkyard.server.parser.EventsParSer.makeEventsListJson(
          events, dao = this, reqer = runAsUser, avatarUrlPrefix)

    val webhookSent = eventsJson map { (evJson: EventAndJson) =>
      WebhookSent(
            webhookId = webhook.webhookId,
            eventId = evJson.event.id,
            attemptNr = 1,

            sentToUrl = webhook.sendToUrl,
            sentAt = now(),
            sentTypes = Nil, // hmm  ImmSeq[EventType],
            sentByAppVer = generatedcode.BuildInfo.dockerTag,  // or  .version?
            sentFormatV = 1,
            sentJson = evJson.json,
            sentHeaders = JsEmptyObj2,

            // We don't know, yet:
            sendFailedAt = None,
            sendFailedHow = None,
            sendFailedMsg = None,
            respAt = None,
            respStatus = None,
            respBody = None)
    }

    Good(webhookSent)
  }


  private def upsertWebhookReq(webhookReqSent: WebhookSent): U = {
    writeTx { (tx, _) =>
      tx.upsertWebhookSent(webhookReqSent)
    }
  }


  private def sendWebhookRequest(webhookSent: WebhookSent): Future[U] = {
    upsertWebhookReq(webhookSent)

    val jsonSt: St = webhookSent.sentJson.toString()
    val wsClient: WSClient = globals.wsClient
    val request: WSRequest =
          wsClient.url(webhookSent.sentToUrl).withHttpHeaders(
              play.api.http.HeaderNames.CONTENT_TYPE -> play.api.http.ContentTypes.JSON,
              //play.api.http.HeaderNames.USER_AGENT -> UserAgent,
              play.api.http.HeaderNames.CONTENT_LENGTH -> jsonSt.length.toString)
              .withRequestTimeout(10.seconds)

    request.post(jsonSt).map({ response: WSResponse =>
      // Now we're in a different thread. Be careful so as not to cause db serialization
      // errors via lock conflicts ... hmm but how?
      try {
        var webhookReqAfter = webhookSent.copy(
              respAt = Some(now()),
              respStatus = Some(response.status),
              respBody = Some(response.body))

        response.status match {
          case 200 =>
            logger.info("Got status 200 from webhook req")
          case x =>
            // [retry_webhook] ?
            logger.warn(s"Got status $x from webhook req")
            webhookReqAfter = webhookReqAfter.copy(
                sendFailedAt = Some(now()),
                sendFailedHow = Some(SendFailedHow.ErrorResponseStatusCode))
        }
        upsertWebhookReq(webhookReqAfter)
      }
      catch {
        case ex: Exception =>
          // This'd be a bug in Talkyard? Then don't retry the webhook.
          logger.warn(s"Error handling webhook response [TyEPWHK1]", ex)
          upsertWebhookReq(
                webhookSent.copy(
                    sendFailedAt = Some(now()),
                    sendFailedHow = Some(SendFailedHow.BugInResponseHandler),
                    sendFailedMsg = Some(ex.getMessage)))
      }
    })(globals.executionContext)
      .recover({
        case ex: Exception =>
          // [retry_webhook] ?
          logger.warn(s"Error sending webhook [TyEPWHK2]", ex)
          upsertWebhookReq(
                webhookSent.copy(
                    sendFailedAt = Some(now()),
                    sendFailedHow = Some(SendFailedHow.CouldntSend),
                    sendFailedMsg = Some(ex.getMessage)))
      })(globals.executionContext)
  }

}
