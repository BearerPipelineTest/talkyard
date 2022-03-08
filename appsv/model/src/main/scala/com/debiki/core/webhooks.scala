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

import play.api.libs.json.JsObject
import com.debiki.core.Prelude.MessAborter


case class Webhook(
  webhookId: WebhookId,

  ownerId:  Opt[PatId],  // who may edit this webhook conf. Null = admins only.
  runAsId:  Opt[PatId],  // only sends events about things that run_as_id_c can see

  enabled: Bo,
  broken: Bo,
  deleted: Bo,
  descr: Opt[St],
  sendToUrl: St,
  sendEventTypes: Set[i32],
  sendFormatV: i16,
  sendMaxPerSec: Opt[i16],

  sentUpToWhen: When,
  sentUpToEventId: Opt[i32],
  maybePendingMin: i16,
  retryEventIds: Set[i32])(mab: MessAborter) {

}



case class WebhookSent(
  webhookId: WebhookId,
  eventId: EventId,
  attemptNr: i32,

  sentToUrl: St,
  sentAt: When,
  sentTypes: ImmSeq[EventType],
  sentByAppVer: St,
  sentFormatV: i32,
  sentJson: JsObject,
  sentHeaders: JsObject,

  sendFailedAt: Opt[When],
  sendFailedHow: Opt[SendFailedHow],
  sendFailedMsg: Opt[St],

  respAt: Opt[When],
  respStatus: Opt[i32],
  respBody: Opt[St],
) {
}



sealed abstract class SendFailedHow(val IntVal: i32) { def toInt: i32 = IntVal }

object SendFailedHow {
  case object CouldntSend extends SendFailedHow(1)
  case object ErrorResponseStatusCode extends SendFailedHow(2)
  case object BugInResponseHandler extends SendFailedHow(3)

  def fromOptInt(value: Opt[i32]): Opt[SendFailedHow] = value map {
    case CouldntSend.IntVal => CouldntSend
    case ErrorResponseStatusCode.IntVal => ErrorResponseStatusCode
    case BugInResponseHandler.IntVal => BugInResponseHandler
    case _ => return None
  }

}
