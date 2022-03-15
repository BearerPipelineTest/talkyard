package talkyard.server.webhooks

import com.debiki.core._
import com.debiki.core.Prelude.MessAborter
import talkyard.server.JsX._
import debiki.JsonUtils._

import play.api.libs.json._


object WebhooksParSer {


  /** Sync w Typescript:  interface Webhook  */
  def JsWebhook(webhook: Webhook): JsObject = {
    //val eventTypeJsNrs: Seq[JsValue] =
    //      webhook.sendEventTypes.toSeq.map(et => JsNumber(et.toInt))
    Json.obj(
          "id" -> webhook.webhookId,
          "ownerId" -> JsNumber(webhook.ownerId),
          "runAsId" -> JsNum32OrNull(webhook.runAsId),
          "enabled" -> webhook.enabled,
          "deleted" -> webhook.deleted,
          "descr" -> JsStringOrNull(webhook.descr),
          "sendToUrl" -> webhook.sendToUrl,
          //"sendEventTypes" -> JsArray(eventTypeJsNrs),
          //"sendEventSubTypes" ->
          "sendFormatV" -> webhook.sendFormatV,
          //"sendMaxPerSec" -> JsNum32OrNull(webhook.sendMaxReqsPerSec),
          "sendMaxEventsPerReq" -> JsNum32OrNull(webhook.sendMaxEventsPerReq),

          "failedHow" -> JsNum32OrNull(webhook.failedHow.map(_.toInt)),
          "failedSince" -> JsWhenMsOrNull(webhook.failedSince),
          "errMsgOrResp" -> JsStringOrNull(webhook.errMsgOrResp),
          "retriedNumTimes" -> JsNum32OrNull(webhook.retriedNumTimes),
          "retriedNumMins" -> JsNum32OrNull(webhook.retriedNumMins),
          "brokenReason" -> JsNum32OrNull(webhook.brokenReason.map(_.toInt)),

          "sentUpToWhen" -> JsWhenMs(webhook.sentUpToWhen),
          "sentUpToEventId" -> JsNum32OrNull(webhook.sentUpToEventId),
          "numPendingMaybe" -> JsNum32OrNull(webhook.numPendingMaybe),
          "doneForNow" -> JsBoolOrNull(webhook.doneForNow),
          // "retryEventIds" -> JsArray(webhook.retryEventIds.toSeq.map(n => JsNumber(n))),
          )
  }


  def parseWebhook(jsVal: JsValue, mab: MessAborter): Webhook = {
    try {
      val jsOb = asJsObject(jsVal, "webhook")
      Webhook(
            webhookId = parseInt32(jsOb, "id"),
            ownerId = parseInt32(jsOb, "ownerId"),
            runAsId = parseOptInt32(jsOb, "runAsId"),
            enabled = parseOptBo(jsOb, "enabled") getOrElse false,
            deleted = parseOptBo(jsOb, "deleted") getOrElse false,
            descr = parseOptSt(jsOb, "descr"),
            sendToUrl = parseSt(jsOb, "sendToUrl"),
            //sendEventTypes = parse..(jsOb, "sendEventTypes"),
            //sendEventSubTypes =
            sendFormatV = parseOptInt32(jsOb, "sendFormatV") getOrElse 1,
            //sendMaxReqsPerSec = parseOptFloat32(jsOb, "sendMaxReqsPerSec"),
            sendMaxEventsPerReq = parseOptInt32(jsOb, "sendMaxEventsPerReq"),
            sendCustomHeaders = parseOptJsObject(jsOb, "sendCustomHeaders"),
            retryMaxSecs = parseOptInt32(jsOb, "retryMaxSecs"),
            retryMaxTimes = parseOptInt32(jsOb, "retryMaxTimes"),

            failedHow = SendFailedHow.fromOptInt(parseOptInt32(jsOb, "failedHow")),
            failedSince = parseOptWhen(jsOb, "failedSince"),
            errMsgOrResp = parseOptSt(jsOb, "errMsgOrResp"),
            retriedNumTimes = parseOptInt32(jsOb, "retriedNumTimes"),
            retriedNumMins = parseOptInt32(jsOb, "retriedNumMins"),
            brokenReason = WebhookBrokenReason.fromOptInt(parseOptInt32(jsOb, "brokenReason")),

            sentUpToWhen = parseOptWhen(jsOb, "sentUpToWhen") getOrElse When.Genesis,
            sentUpToEventId = parseOptInt32(jsOb, "sentUpToEventId"),
            numPendingMaybe = parseOptInt32(jsOb, "numPendingMaybe"),
            doneForNow = parseOptBo(jsOb, "doneForNow"),
            // retryEventIds = Set.empty, parseInt32(jsOb, "retryEventIds"),
            )(mab)
    }
    catch {
      case ex: BadJsonException =>
        mab.abort("TyEJSNWBHK", s"Invalid webhook JSON: ${ex.getMessage}")
    }
  }


  /** Sync w Typescript:  interface WebhookReqOut  */
  def JsWebhookReqOut(reqSent: WebhookReqOut): JsObject = {
    Json.obj(
          "webhookId" -> reqSent.webhookId,
          "sentAt" -> JsWhenMs(reqSent.sentAt),
          "sentToUrl" -> reqSent.sentToUrl,
          "sentByAppVer" -> reqSent.sentByAppVer,
          "sentFormatV" -> reqSent.sentFormatV,
          "sentEventTypes" -> JsArray(reqSent.sentEventTypes.toSeq.map(t => JsNumber(t.toInt))),
          // sentEventSubTypes
          "sentEventIds" -> JsArray(reqSent.sentEventIds.toSeq.map(id => JsNumber(id))),
          "sentJson" -> reqSent.sentJson,
          "sentHeaders" -> JsObjOrNull(reqSent.sentHeaders),

          "failedAt" -> JsWhenMsOrNull(reqSent.failedAt),
          "failedHow" -> JsNum32OrNull(reqSent.failedHow.map(_.toInt)),
          "errMsg" -> JsStringOrNull(reqSent.errMsg),

          "respAt" -> JsWhenMsOrNull(reqSent.respAt),
          "respStatus" -> JsNum32OrNull(reqSent.respStatus),
          "respBody" -> JsStringOrNull(reqSent.respBody))
  }


}
