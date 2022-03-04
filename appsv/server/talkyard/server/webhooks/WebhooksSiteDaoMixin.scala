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
import org.apache.commons.codec.{binary => acb}



trait WebhooksSiteDaoMixin {
  self: SiteDao =>


  def sendPendingWebhookReqs(webhooks: ImmSeq[Webhook]): U = {
    webhooks foreach { w =>
      sendReqsForOneWebhook(w)
    }
  }

  def sendReqsForOneWebhook(webhook: Webhook): U = {
    readTx { tx =>
      tx.loadEventsFromAuditLog(newerOrAt = webhook.sentUpToWhen,
            newerThanEventId = webhook.sentUpToEventId, limit = 1)
    }
  }
}
