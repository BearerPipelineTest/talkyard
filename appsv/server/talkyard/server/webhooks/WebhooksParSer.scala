package talkyard.server.webhooks

import com.debiki.core._
import com.debiki.core.Prelude.MessAborter
import talkyard.server.JsX._
import debiki.JsonUtils._

import play.api.libs.json._


object WebhooksParSer {

  def JsWebhook(webhook: Webhook): JsObject = {
    val eventTypeJsNrs: Seq[JsValue] = webhook.sendEventTypes.toSeq.map(n => JsNumber(n))
    Json.obj(
          "id" -> webhook.webhookId,
          "ownerId" -> JsNum32OrNull(webhook.ownerId),
          "runAsId" -> JsNum32OrNull(webhook.runAsId),
          "enabled" -> webhook.enabled,
          "broken" -> webhook.broken,
          "deleted" -> webhook.deleted,
          "descr" -> JsStringOrNull(webhook.descr),
          "sendToUrl" -> webhook.sendToUrl,
          "sendEventTypes" -> JsArray(eventTypeJsNrs),
          "sendFormatV" -> webhook.sendFormatV,
          "sendMaxPerSec" -> JsNum16OrNull(webhook.sendMaxPerSec),
          "sentUpToWhen" -> JsWhenMs(webhook.sentUpToWhen),
          "sentUpToEventId" -> JsNum32OrNull(webhook.sentUpToEventId),
          "maybePendingMin" -> webhook.maybePendingMin,
          "retryEventIds" -> JsArray(webhook.retryEventIds.toSeq.map(n => JsNumber(n))),
          )
  }

  def parseWebhook(jsVal: JsValue, mab: MessAborter): Webhook = {
    try {
      val jsOb = asJsObject(jsVal, "webhook")
      Webhook(
            webhookId = parseInt32(jsOb, "id"),
            ownerId = parseOptInt32(jsOb, "ownerId"),
            runAsId = parseOptInt32(jsOb, "runAsId"),
            enabled = parseBo(jsOb, "enabled"),
            broken = parseBo(jsOb, "broken"),
            deleted = parseBo(jsOb, "deleted"),
            descr = parseOptSt(jsOb, "descr"),
            sendToUrl = parseSt(jsOb, "sendToUrl"),
            sendEventTypes = Set.empty, // parse..(jsOb, "sendEventTypes"),
            sendFormatV = parseInt16(jsOb, "sendFormatV"),
            sendMaxPerSec = parseOptInt16(jsOb, "sendMaxPerSec"),
            sentUpToWhen = parseWhen(jsOb, "sentUpToWhen"),
            sentUpToEventId = parseOptInt32(jsOb, "sentUpToEventId"),
            maybePendingMin = parseInt16(jsOb, "maybePendingMin"),
            retryEventIds = Set.empty, // parseInt32(jsOb, "retryEventIds"),
            )(mab)
    }
    catch {
      case ex: BadJsonException =>
        mab.abort("TyEJSNWBHK", s"Invalid webhook JSON: ${ex.getMessage}")
    }
  }


}
