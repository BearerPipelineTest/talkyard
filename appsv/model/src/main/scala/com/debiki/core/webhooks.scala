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

package com.debiki.core

import com.debiki.core.Prelude._
import play.api.libs.json.JsObject
import com.debiki.core.Prelude.MessAborter
import com.debiki.core.SendFailedHow.BadConfig


case class Webhook(
  webhookId: WebhookId,

  // Who may edit this webhook conf. For now, admins only.
  ownerId:  PatId,

  // Only sends events about things that run_as_id_c can see. None means strangers.
  runAsId:  Opt[PatId],

  enabled: Bo,
  deleted: Bo,
  descr: Opt[St],
  sendToUrl: St,
  //checkDestCert: Bo,
  //sendEventTypes: Set[EventType],
  //sendEventSubTypes: Set[EventSubType],
  sendFormatV: i32,
  //sendMaxReqsPerSec: Opt[f32],
  sendMaxEventsPerReq: Opt[i32],
  //sendMaxDelaySecs: i32,
  sendCustomHeaders: Opt[JsObject],
  retryMaxSecs: Opt[i32],
  retryMaxTimes: Opt[i32],  // rename to retryExtraTimes?  after retryMaxSecs?

  failedHow: Opt[SendFailedHow] = None,
  failedSince: Opt[When] = None,
  errMsgOrResp: Opt[St] = None,
  errorResp: Opt[St] = None,
  retriedNumTimes: Opt[i32] = None,
  retriedNumMins: Opt[i32] = None,
  brokenReason: Opt[WebhookBrokenReason] = None,

  sentUpToWhen: When,
  sentUpToEventId: Opt[i32],
  numPendingMaybe: Opt[i32],
  doneForNow: Opt[Bo],
  //retryEventIds: Set[i32]
  )(mab: MessAborter) {

  mab.check(webhookId >= 1, "TyEWBHKS01")
  // For now:
  mab.abortIf(ownerId != Group.AdminsId, "TyE03MEJA46", "ownerId must be the admins group")
  mab.abortIf(runAsId isNot SysbotUserId, "TyE03MEJA47", "runAsId must be sysbot")
  mab.abortIf(descr.exists(_.length > 250), "TyE03MEJA47")  // [db_constrs]
  mab.abortIf(sendToUrl.obviouslyBadUrl, "")

  //sendMaxReqsPerSec
  mab.check(sendMaxEventsPerReq.forall(_ >= 1),
    "TyEWBHK0BRNK2", s"Must send >= 1 events per request, but sendMaxEventsPerReq is: ${
          sendMaxEventsPerReq}")
  //sendMaxDelaySecs

  mab.check(retryMaxSecs.forall(_ >= 0),
      "TyEWBHK0BRNK2", "Cannot retry a webhook < 0 seconds")
  mab.check(retryMaxTimes.isEmpty || isBroken,
      "TyEWBHK0BRNK", "Cannot retry a webhook that isn't broken")
  mab.check(retryMaxTimes.forall(_ >= 1),
      "TyEWBHK0BRNK2", "Cannot retry a webhook <= 0 times")

  mab.check(retriedNumTimes.forall(_ >= 0), "TyEWBHK052MD")
  mab.check(retriedNumMins.forall(_ >= 0), "TyEWBHK052MC")
  mab.check(sentUpToEventId.forall(_ >= 1), "TyEWBHK052MW")
  mab.check(numPendingMaybe.forall(_ >= 0), "TyEWBHK052MB")


  def isBroken: Bo = brokenReason.isDefined


  def copyAsWorking(
        sentUpToWhen: When,
        sentUpToEventId: Opt[i32],
        numPendingMaybe: Opt[i32],
        doneForNow: Opt[Bo]): Webhook =
    copy(failedHow = None,
          failedSince = None,
          errMsgOrResp = None,
          retriedNumTimes = None,
          retriedNumMins = None,
          brokenReason = None,
          sentUpToWhen = sentUpToWhen,
          sentUpToEventId = sentUpToEventId,
          numPendingMaybe = numPendingMaybe,
          doneForNow = doneForNow)(IfBadDie)


  def copyWithFailure(failedReq: WebhookReqOut, now: When, retryMaxSecs: i32): Webhook = {
    val failedHow = failedReq.failedHow.getOrDie(
          "TyE603MEPJ4", "The webhook req didn't fail; cannot copy-with-failure")

    val failedMinutes = failedSince.map(now.minutesSince).map(_.toInt) getOrElse 0

    val brokenReason = failedHow match {
      case SendFailedHow.BadConfig => Some(WebhookBrokenReason.BadConfig)
      case SendFailedHow.TalkyardBug => Some(WebhookBrokenReason.TalkyardBug)
      case _ =>
        if (failedMinutes <= retryMaxSecs) None
        else Some(WebhookBrokenReason.RequestFails)
    }

    copy(failedHow = Some(failedHow),
          failedSince = Some(now),
          errMsgOrResp = failedReq.errMsg orElse failedReq.respBody,
          retriedNumTimes = retriedNumTimes.map(_ + 1) orElse Some(0),
          retriedNumMins = Some(failedMinutes),
          brokenReason = brokenReason, // Some(WebhookBrokenReason.fromSendFailedHow(failedHow))
          // Don't update sentUpToWhen etc.
          )(IfBadDie)
  }
}



sealed abstract class WebhookBrokenReason(val IntVal: i32) { def toInt: i32 = IntVal }

object WebhookBrokenReason {
  case object BadConfig extends WebhookBrokenReason(1)
  case object RequestFails extends WebhookBrokenReason(2)
  case object AdminsMarkedItBroken extends WebhookBrokenReason(3)
  case object TalkyardBug extends WebhookBrokenReason(9)

  def fromOptInt(value: Opt[i32]): Opt[WebhookBrokenReason] = value map {
    case BadConfig.IntVal => BadConfig
    case RequestFails.IntVal => RequestFails
    case TalkyardBug.IntVal => TalkyardBug
    case AdminsMarkedItBroken.IntVal => AdminsMarkedItBroken
    case _ => return None
  }

  /*
  def fromSendFailedHow(how: SendFailedHow): WebhookBrokenReason = how match {
    case SendFailedHow.BadConfig => BadConfig
    case SendFailedHow.TalkyardBug => TalkyardBug
    case _ => RequestFails
  } */
}



case class WebhookReqOut(
  webhookId: WebhookId,
  sentAt: When,
  sentToUrl: St,
  sentByAppVer: St,
  sentFormatV: i32,
  sentEventTypes: Set[EventType],
  //sentEventSubTypes: Set[EventSubType],
  sentEventIds: Set[EventId],
  sentJson: JsObject,
  sentHeaders: Opt[JsObject],

  failedAt: Opt[When] = None,
  failedHow: Opt[SendFailedHow] = None,
  errMsg: Opt[St] = None,

  respAt: Opt[When] = None,
  respStatus: Opt[i32] = None,
  respStatusText: Opt[St] = None,
  respBody: Opt[St] = None,
  respHeaders: Opt[JsObject] = None,
) {

  require(webhookId >= 1, "TyEWBHKSNC01")
  require(sentToUrl.nonEmpty, "TyEWBHKSNC02")
  require(sentByAppVer.isTrimmedNonEmpty, "TyEWBHKSNC03")
  require(sentFormatV == 1, "TyEWBHKSNC04")
  //require(sentEventTypes.nonEmpty, "TyEWBHKSNC05")
  //require(sentEventSubTypes.nonEmpty, "TyEWBHKSNC05B")
  require(sentEventIds.nonEmpty, "TyEWBHKSNC06")
  //require(is-headers-single-map(sentHeaders), "TyEWBHKSNC06B")

  require(failedAt.isDefined == failedHow.isDefined, "TyEWBHKSNC07")
  require(failedAt.isDefined || errMsg.isEmpty, "TyEWBHKSNC08")

  require(respAt.isDefined == respStatus.isDefined, "TyEWBHKSNC09")

  // [db_constr]
  require(respStatusText.forall(_.length < 120), "TyEWBHKSNC0E")

  // The reply could be just 200 OK — without any response body or anything.
  require(respAt.isDefined || respStatusText.isEmpty, "TyEWBHKSNC0C")
  require(respAt.isDefined || respBody.isEmpty, "TyEWBHKSNC0A")
  require(respAt.isDefined || respHeaders.isEmpty, "TyEWBHKSNC0B")
  //require(is-headers-multi-map(respHeaders), "TyEWBHKSNC06B2")

  // If we got a response, then, shouldn't have set errMsg, since the
  // request actually succeeded.
  require(errMsg.isEmpty || respBody.isEmpty, "TyEWBHKSNC0D")

}



sealed abstract class SendFailedHow(val IntVal: i32) { def toInt: i32 = IntVal }

object SendFailedHow {
  case object BadConfig extends SendFailedHow(21)
  case object CouldntConnect extends SendFailedHow(31)
  case object RequestTimedOut extends SendFailedHow(41)
  case object OtherException extends SendFailedHow(51)
  case object ErrorResponseStatusCode extends SendFailedHow(61)
  case object TalkyardBug extends SendFailedHow(99)

  def fromOptInt(value: Opt[i32]): Opt[SendFailedHow] = value map {
    case BadConfig.IntVal => BadConfig
    case CouldntConnect.IntVal => CouldntConnect
    case RequestTimedOut.IntVal => RequestTimedOut
    case OtherException.IntVal => OtherException
    case ErrorResponseStatusCode.IntVal => ErrorResponseStatusCode
    case TalkyardBug.IntVal => TalkyardBug
    case _ => return None
  }
}
