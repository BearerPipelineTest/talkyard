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
import talkyard.server.{TyContext, TyController}
import talkyard.server.http._
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._


import WebhooksParSer._

class WebhooksController @Inject()(cc: ControllerComponents, tyContext: TyContext)
  extends TyController(cc, tyContext) {


  def listWebhooks(): Action[U] = AdminGetAction { request: GetRequest =>
    listWebhooksImpl(request)
  }


  private def listWebhooksImpl(request: DebikiRequest[_]): Result = {
    import request.dao
    val webhooks = dao.writeTx { (tx, _) =>
      tx.loadWebhooks()
    }
    OkSafeJson(Json.obj(
        "webhooks" -> JsArray(webhooks map JsWebhook)))
  }


  def upsertWebhook: Action[JsValue] = AdminPostJsonAction(maxBytes = 5000) {
        request: JsonPostRequest =>
    import request.{body, dao}
    val webhook = WebhooksParSer.parseWebhook(body, IfBadAbortReq)
    dao.writeTx { (tx, _) =>
      tx.upsertWebhook(webhook)
    }
    OkSafeJson(Json.obj(
        "webhook" -> JsWebhook(webhook)))
  }


  def deleteWebhook: Action[JsValue] = AdminPostJsonAction(maxBytes = 5000) {
        request: JsonPostRequest =>
    // Maybe makes sense to allow this, even if API not enabled, so one can still
    // delete old secrets?
    import request.{dao, body}
    import debiki.JsonUtils._

    val webhookId = parseInt32(body, "webhookId")
    dao.writeTx { (tx, _) =>
      tx.deleteWebhook(webhookId)
    }
    listWebhooksImpl(request)
  }

}
