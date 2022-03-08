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
trait WebhooksRdbMixin extends SiteTx {
  self: RdbSiteTransaction =>


  def loadWebhooks(): ImmSeq[Webhook] = {
    val query = s"""
          select * from webhooks_t
          where site_id_c = ?
            and not deleted_c """
    runQueryFindMany(query, List(siteId.asAnyRef), parseWebhook)
  }


  def upsertWebhook(webhook: Webhook): U = {
    val statement = s"""
          insert into webhooks_t (
              site_id_c,
              webhook_id_c,
              owner_id_c,
              run_as_id_c,
              enabled_c,
              broken_c,
              deleted_c,
              descr_c,
              send_to_url_c,
              send_event_types_c,
              send_format_v_c,
              send_max_per_sec_c,
              sent_up_to_when_c,
              sent_up_to_event_id_c,
              maybe_pending_min_c,
              retry_event_ids_c)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          on conflict (site_id_c, webhook_id_c)   -- pk
          do update set
              owner_id_c = excluded.owner_id_c,
              run_as_id_c = excluded.run_as_id_c,
              enabled_c = excluded.enabled_c,
              broken_c = excluded.broken_c,
              deleted_c = excluded.deleted_c,
              descr_c = excluded.descr_c,
              send_to_url_c = excluded.send_to_url_c,
              send_event_types_c = excluded.send_event_types_c,
              send_format_v_c = excluded.send_format_v_c,
              send_max_per_sec_c = excluded.send_max_per_sec_c,
              sent_up_to_when_c = excluded.sent_up_to_when_c,
              sent_up_to_event_id_c = excluded.sent_up_to_event_id_c,
              maybe_pending_min_c = excluded.maybe_pending_min_c,
              retry_event_ids_c = excluded.retry_event_ids_c
            """

    val values = List(
          siteId.asAnyRef,
          webhook.webhookId.asAnyRef,
          webhook.ownerId.orNullInt,
          webhook.runAsId.orNullInt,
          webhook.enabled.asAnyRef,
          webhook.broken.asAnyRef,
          webhook.deleted.asAnyRef,
          webhook.descr.trimOrNullVarchar,
          webhook.sendToUrl,
          NullVarchar,  // webhook.sendEventTypes
          1.asAnyRef,   // send format
          NullInt,      // webhook.sendMaxPerSec
          webhook.sentUpToWhen.asTimestamp,
          webhook.sentUpToEventId.orNullInt,
          )

    runUpdateSingleRow(statement, values)
  }


  def deleteWebhook(webhookId: WebhookId): U = {
    unimpl("TyE32057MRT")
  }


  def loadWebhooksSent(): ImmSeq[WebhookSent] = {
    val query = s"""
          select * from webhooks_sent_t
          where site_id_c = ?
            """
    runQueryFindMany(query, List(siteId.asAnyRef), parseWebhookSent)
  }


  def upsertWebhookSent(webhookSent: WebhookSent): U = {
    val statement = s"""
          insert into webhooks_sent_t (
              site_id_c,
              webhook_id_c,
              event_id_c,
              attempt_nr_c,
              sent_to_url_c,
              sent_at_c,
              sent_types_c,
              sent_by_app_ver_c,
              sent_format_v_c,
              sent_json_c,
              sent_headers_c,
              send_failed_at_c,
              send_failed_how_c,
              send_failed_msg_c,
              resp_at_c,
              resp_status_c,
              resp_body_c,
              )
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          on conflict (site_id_c, webhook_id_c, event_id_c, attempt_nr_c)
          do update set
              sent_to_url_c = excluded.sent_to_url_c,
              sent_at_c = excluded.sent_at_c,
              sent_types_c = excluded.sent_types_c,
              sent_by_app_ver_c = excluded.sent_by_app_ver_c,
              sent_format_v_c = excluded.sent_format_v_c,
              sent_json_c = excluded.sent_json_c,
              sent_headers_c = excluded.sent_headers_c,
              send_failed_at_c = excluded.send_failed_at_c,
              send_failed_how_c = excluded.send_failed_how_c,
              send_failed_msg_c = excluded.send_failed_msg_c,
              resp_at_c = excluded.resp_at_c,
              resp_status_c = excluded.resp_status_c,
              resp_body_c = excluded.resp_body_c
            """

    val values = List(
          siteId.asAnyRef,
          webhookSent.webhookId.asAnyRef,
          webhookSent.eventId.asAnyRef,
          webhookSent.attemptNr.asAnyRef,
          webhookSent.sentToUrl,
          webhookSent.sentAt.asTimestamp,
          webhookSent.sentTypes,
          webhookSent.sentByAppVer,
          webhookSent.sentFormatV.asAnyRef,
          webhookSent.sentJson, // ?
          webhookSent.sentHeaders, // ? JsObject,
          webhookSent.sendFailedAt.orNullTimestamp,
          webhookSent.sendFailedHow.map(_.toInt).orNullInt,
          webhookSent.sendFailedMsg.orNullVarchar,
          webhookSent.respAt.orNullTimestamp,
          webhookSent.respStatus.orNullInt,
          webhookSent.respBody.orNullVarchar,
          )

    runUpdateSingleRow(statement, values)
  }
}


object WebhooksRdb {


  def parseWebhook(rs: j_ResultSet): Webhook = {
    Webhook(
          webhookId = getInt32(rs, "webhook_id_c"),

          ownerId = getOptInt32(rs, "owner_id_c"),
          runAsId = getOptInt32(rs, "run_as_id_c"),

          enabled = getBool(rs, "enabled_c"),
          broken = getBool(rs, "broken_c"),
          deleted = getBool(rs, "deleted_c"),
          descr = getOptString(rs, "descr_c"),
          sendToUrl = getString(rs, "send_to_url_c"),
          sendEventTypes = Set.empty, // getOptArrayOfInt32(rs, "send_event_types_c"),
          sendFormatV = 1.toShort, //getOptInt32(rs, "send_format_v_c"),
          sendMaxPerSec = getOptInt16(rs, "send_max_per_sec_c"),

          sentUpToWhen = getWhen(rs, "sent_up_to_when_c"),
          sentUpToEventId = getOptInt32(rs, "sent_up_to_event_id_c"),
          maybePendingMin = getInt16(rs, "maybe_pending_min_c"),
          retryEventIds = Set.empty, //getOptArrayOfInt32(rs, "retry_event_ids_c"))
          )(IfBadDie)
  }


  def parseWebhookSent(rs: j_ResultSet): WebhookSent = {
    WebhookSent(
          webhookId = getInt32(rs, "webhook_id_c"),
          eventId = getInt32(rs, "event_id_c"),
          attemptNr = getInt32(rs, "attempt_nr_c"),

          // sent_type_c  webhook_type_d, -- what? why?  hmm, maybe json/paseto/sth-else?
          sentToUrl = getString(rs, "sent_to_url_c"),
          sentAt = getWhen(rs, "sent_at_c"),
          sentTypes = Nil, // sent_types_c
          sentByAppVer = getString(rs, "sent_by_app_ver_c"),
          sentFormatV = getInt32(rs, "sent_format_v_c"),
          sentJson = getJsObject(rs, "sent_json_c"),
          sentHeaders = getJsObject(rs, "sent_headers_c"),

          sendFailedAt = getOptWhen(rs, "send_failed_at_c"),
          sendFailedHow = SendFailedHow.fromOptInt(getOptInt32(rs, "send_failed_how_c")),
          sendFailedMsg = getOptString(rs, "send_failed_msg_c"),

          respAt = getOptWhen(rs, "resp_at_c"),
          respStatus = getOptInt32(rs, "resp_status_c"),
          respBody = getOptString(rs, "resp_body_c"),
          )
  }

}
