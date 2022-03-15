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

package com.debiki.dao.rdb

import com.debiki.core._
import com.debiki.core.Prelude._
import Rdb._
import RdbUtil._
import java.sql.{ResultSet => j_ResultSet, SQLException => j_SQLException}
import collection.{mutable => mut}
import WebhooksRdb._


/** Manages user sessions. But why are sessions stored in Postgres, not Redis?
  * For security reasons. It's good to be able to:
  * - Look up all current sessions by a certain user.
  * - Look up all sessions by ip addr.
  * - List sessions by time and user / ip.
  * - Remember if a session's ip address changes (could be suspicious — is there
  *   enough time for the person to travel to the new location?).
  * - Delete some or all of a user's session, and remember who did this and when.
  * - Delete all posts made by a session — in case someone got pwned.
  *
  * All that would be doable in Redis, but it's simpler, in Postgres. And,
  * combined with an app server in-process mem cache, this approach can also be made
  * faster than Redis (which is an out of process mem cache, slower).
  *
  * Maybe some time later, sessions will need to be temporarily buffered in Redis
  * or elsewhere somehow, and only written to Postgres every once in a while
  * (except for when logging out — that'd get persisted immediately).
  * Or even some other type of storage.  But that's in the distant future.
  */
trait WebhooksRdbMixin extends SiteTransaction {
  self: RdbSiteTransaction =>


  def loadWebhook(id: WebhookId): Opt[Webhook] = {
    loadWebhooksImpl(Some(id)).headOption
  }


  def loadAllWebhooks(): ImmSeq[Webhook] = {
    loadWebhooksImpl(None)
  }


  private def loadWebhooksImpl(anyId: Opt[WebhookId]): ImmSeq[Webhook] = {
    val values = MutArrBuf[AnyRef]()
    values.append(siteId.asAnyRef)

    val andIdEq = anyId map { id =>
      values.append(id.asAnyRef)
      "and webhook_id_c = ?"
    } getOrElse ""

    val query = s"""
          select * from webhooks_t
          where site_id_c = ?
            and not deleted_c
            $andIdEq
          order by webhook_id_c """
    runQueryFindMany(query, values.toList, parseWebhook)
  }


  def upsertWebhook(webhook: Webhook): U = {
    val statement = s"""
          insert into webhooks_t (
              site_id_c,
              webhook_id_c,
              owner_id_c,
              run_as_id_c,
              enabled_c,
              deleted_c,
              descr_c,
              send_to_url_c,
              send_event_types_c,
              send_event_subtypes_c,
              send_format_v_c,
              send_max_reqs_per_sec_c,
              send_max_events_per_req_c,
              send_max_delay_secs_c,
              send_custom_headers_c,
              retry_max_secs_c,
              retry_max_times_c,
              failed_reason_c,
              failed_since_c,
              failed_message_c,
              retried_num_times_c,
              retried_num_mins_c,
              broken_reason_c,
              sent_up_to_when_c,
              sent_up_to_event_id_c,
              num_pending_maybe_c,
              done_for_now_c
              -- retry_event_ids_c,
              )
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?, ?, ?, ?, ?)
          on conflict (site_id_c, webhook_id_c)   -- pk
          do update set
              owner_id_c = excluded.owner_id_c,
              run_as_id_c = excluded.run_as_id_c,
              enabled_c = excluded.enabled_c,
              deleted_c = excluded.deleted_c,
              descr_c = excluded.descr_c,
              send_to_url_c = excluded.send_to_url_c,
              send_event_types_c = excluded.send_event_types_c,
              send_event_subtypes_c = excluded.send_event_subtypes_c,
              send_format_v_c = excluded.send_format_v_c,
              send_max_reqs_per_sec_c = excluded.send_max_reqs_per_sec_c,
              send_max_events_per_req_c = excluded.send_max_events_per_req_c,
              send_max_delay_secs_c = excluded.send_max_delay_secs_c,
              send_custom_headers_c = excluded.send_custom_headers_c,
              retry_max_secs_c = excluded.retry_max_secs_c,
              retry_max_times_c = excluded.retry_max_times_c,
              failed_reason_c = excluded.failed_reason_c,
              failed_since_c = excluded.failed_since_c,
              failed_message_c = excluded.failed_message_c,
              retried_num_times_c = excluded.retried_num_times_c,
              retried_num_mins_c = excluded.retried_num_mins_c,
              broken_reason_c = excluded.broken_reason_c,
              sent_up_to_when_c = excluded.sent_up_to_when_c,
              sent_up_to_event_id_c = excluded.sent_up_to_event_id_c,
              num_pending_maybe_c = excluded.num_pending_maybe_c,
              done_for_now_c = excluded.done_for_now_c
              -- retry_event_ids_c = excluded.retry_event_ids_c  """

    val values = List(
          siteId.asAnyRef,
          webhook.webhookId.asAnyRef,
          webhook.ownerId.asAnyRef,
          webhook.runAsId.orNullInt32,
          webhook.enabled.asAnyRef,
          webhook.deleted.asAnyRef,
          webhook.descr.trimOrNullVarchar,
          webhook.sendToUrl,
          NullArray,  // makeSqlArrayOfInt32(webhook.sendEventTypes)
          NullArray,  // makeSqlArrayOfInt32(webhook.sendEventsubTypes)
          webhook.sendFormatV.asAnyRef,
          NullInt,      // webhook.sendMaxReqsPerSec
          webhook.sendMaxEventsPerReq.orNullInt32,
          NullInt,      // webhook.sendMaxDelaySecs
          webhook.sendCustomHeaders.orNullJson,
          webhook.retryMaxSecs.orNullInt32,
          webhook.retryMaxTimes.orNullInt32,

          webhook.failedHow.map(_.toInt).orNullInt32,
          webhook.failedSince.orNullTimestamp,
          webhook.errMsgOrResp.orNullVarchar,
          webhook.retriedNumTimes.orNullInt32,
          webhook.retriedNumMins.orNullInt32,
          webhook.brokenReason.map(_.toInt).orNullInt32,

          webhook.sentUpToWhen.asTimestamp,
          webhook.sentUpToEventId.orNullInt32,
          webhook.numPendingMaybe.orNullInt32,
          webhook.doneForNow.orNullBo)

    runUpdateSingleRow(statement, values)
  }


  def deleteWebhook(webhookId: WebhookId): U = {
    unimpl("TyE32057MRT")
  }


  def loadWebhookReqsOutRecentFirst(limit: i32): ImmSeq[WebhookReqOut] = {
    val query = s"""
          select * from webhook_reqs_out_t
          where site_id_c = ?
          order by sent_at_c desc limit $limit  """
    runQueryFindMany(query, List(siteId.asAnyRef), parseWebhookSent)
  }


  def insertWebhookReqOut(reqOut: WebhookReqOut): U = {
    val statement = s"""
          insert into webhook_reqs_out_t (
              site_id_c,
              webhook_id_c,
              sent_at_c,
              sent_to_url_c,
              sent_by_app_ver_c,
              sent_format_v_c,
              sent_event_types_c,
              sent_event_subtypes_c,
              sent_event_ids_c,
              sent_json_c,
              sent_headers_c)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) """

    val values = List(
          siteId.asAnyRef,
          reqOut.webhookId.asAnyRef,
          reqOut.sentAt.asTimestamp,
          reqOut.sentToUrl,
          reqOut.sentByAppVer,
          reqOut.sentFormatV.asAnyRef,
          makeSqlArrayOfInt32(reqOut.sentEventTypes.map(_.toInt)),
          NullArray, //reqOut.sentEventSubTypes,
          makeSqlArrayOfInt32(reqOut.sentEventIds),
          reqOut.sentJson,
          reqOut.sentHeaders.orNullJson)

    runUpdateSingleRow(statement, values)
  }


  def updateWebhookReqOut(reqOut: WebhookReqOut): U = {
    val statement = s"""
          update webhook_reqs_out_t set
              failed_at_c    = ?,
              failed_how_c   = ?,
              failed_msg_c   = ?,
              resp_at_c      = ?,
              resp_status_c  = ?,
              resp_status_text_c = ?,
              resp_body_c    = ?,
              resp_headers_c = ?
          where
              site_id_c = ? and
              webhook_id_c = ? and
              sent_at_c = ?  """

    val values = List(
          reqOut.failedAt.orNullTimestamp,
          reqOut.failedHow.map(_.toInt).orNullInt,
          reqOut.errMsg.orNullVarchar,
          reqOut.respAt.orNullTimestamp,
          reqOut.respStatus.orNullInt,
          reqOut.respStatusText.orNullVarchar,
          reqOut.respBody.orNullVarchar,
          reqOut.respHeaders.orNullJson,
          siteId.asAnyRef,
          reqOut.webhookId.asAnyRef,
          reqOut.sentAt.asTimestamp)

    runUpdateSingleRow(statement, values)
  }
}


object WebhooksRdb {


  def parseWebhook(rs: j_ResultSet): Webhook = {
    Webhook(
          webhookId = getInt32(rs, "webhook_id_c"),

          ownerId = getInt32(rs, "owner_id_c"),
          runAsId = getOptInt32(rs, "run_as_id_c"),

          enabled = getBool(rs, "enabled_c"),
          deleted = getBool(rs, "deleted_c"),
          descr = getOptString(rs, "descr_c"),
          sendToUrl = getString(rs, "send_to_url_c"),
          // sendEventTypes = getOptArrayOfInt32(rs, "send_event_types_c"),
          sendFormatV = 1.toShort, //getOptInt32(rs, "send_format_v_c"),
          //sendMaxReqsPerSec = getOptInt32(rs, "send_max_reqs_per_sec_c"),
          sendMaxEventsPerReq = getOptInt32(rs, "send_max_events_per_req_c"),
          // sendMaxDelaySecs  = send_max_delay_secs_c
          sendCustomHeaders = getOptJsObject(rs, "send_custom_headers_c"),
          retryMaxSecs = getOptInt32(rs, "retry_max_secs_c"),
          retryMaxTimes = getOptInt32(rs, "retry_max_times_c"),

          failedHow = SendFailedHow.fromOptInt(getOptInt32(rs, "failed_reason_c")),
          failedSince = getOptWhen(rs, "failed_since_c"),
          errMsgOrResp = getOptString(rs, "failed_message_c"),
          retriedNumTimes = getOptInt32(rs, "retried_num_times_c"),
          retriedNumMins = getOptInt32(rs, "retried_num_mins_c"),
          brokenReason = WebhookBrokenReason.fromOptInt(getOptInt32(rs, "broken_reason_c")),

          sentUpToWhen = getWhen(rs, "sent_up_to_when_c"),
          sentUpToEventId = getOptInt32(rs, "sent_up_to_event_id_c"),
          numPendingMaybe = getOptInt32(rs, "num_pending_maybe_c"),
          doneForNow = getOptBool(rs, "done_for_now_c"),
          //retryEventIds = Set.empty, //getOptArrayOfInt32(rs, "retry_event_ids_c"))
          )(IfBadDie)
  }


  def parseWebhookSent(rs: j_ResultSet): WebhookReqOut = {
    WebhookReqOut(
          webhookId = getInt32(rs, "webhook_id_c"),
          sentAt = getWhen(rs, "sent_at_c"),

          // sent_type_c  webhook_type_d, -- what? why?  hmm, maybe json/paseto/sth-else?
          sentToUrl = getString(rs, "sent_to_url_c"),
          sentByAppVer = getString(rs, "sent_by_app_ver_c"),
          sentFormatV = getInt32(rs, "sent_format_v_c"),
          sentEventTypes = getArrayOfInt32(rs, "sent_event_types_c").toSet
                              .flatMap(EventType.fromInt),
          //sentEventSubTypes = sent_event_subtypes_c
          sentEventIds = getArrayOfInt32(rs, "sent_event_ids_c").toSet,
          sentJson = getJsObject(rs, "sent_json_c"),
          sentHeaders = getOptJsObject(rs, "sent_headers_c"),

          failedAt = getOptWhen(rs, "failed_at_c"),
          failedHow = SendFailedHow.fromOptInt(getOptInt32(rs, "failed_how_c")),
          errMsg = getOptString(rs, "failed_msg_c"),

          respAt = getOptWhen(rs, "resp_at_c"),
          respStatus = getOptInt32(rs, "resp_status_c"),
          respStatusText = getOptString(rs, "resp_status_text_c"),
          respBody = getOptString(rs, "resp_body_c"),
          respHeaders = getOptJsObject(rs, "resp_headers_c"),
          )
  }

}
