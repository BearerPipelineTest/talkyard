package talkyard.server.parser

import com.debiki.core._
import talkyard.server.JsX

import play.api.libs.json._


object EventsParSer {

  def JsPageEventTypeString_apiv0(eventType: PageEventType): JsString = {
    val ET = PageEventType
    val asSt = eventType match {
      case ET.PageCreated => "Page.Created"

      case ET.PageDeleted => "Page.Deleted"
      case ET.PageUndeleted => "Page.Undeleted"

      case ET.PageClosed => "Page.Closed"
      case ET.PageReopened => "Page.Reopened"

      case ET.PageAnswered => "Page.Answered"
      //case ET.PageUnanswered

      case ET.PageDone => "Page.Done"
    }

    assert(asSt startsWith "Page.")
    JsString(asSt)
  }

  def JsPostEventTypeString_apiv0(eventType: PostEventType): JsString = {
    val ET = PostEventType
    val asSt = eventType match {
      case ET.PostCreated => "Post.Created"
      case ET.PostEdited => "Post.Edited"

      //case ET.PostDeleted => "Post.Deleted"
      //case ET.PostUndeleted => "Post.Undeleted"
    }

    assert(asSt startsWith "Post.")
    JsString(asSt)
  }

  /* Participants?
  def JsPatEventTypeString_apiv0(eventType: PatEventType): JsString = {
    assert(asSt startsWith "User.")  and  "Group."  and  "Guest."  ?
                    or just  "Pat."  for all of them, and a type field?
                    Maybe both  User.Created   and  Pat.Created,
                    and the client can then choose?
    JsString(asSt)
  } */


  def JsPageEvent_apiv0(event: PageEvent): JsObject = {
    Json.obj(
        "id" -> event.id,
        "eventTypes" -> JsArray(event.eventTypes map JsPageEventTypeString_apiv0),
        "when" -> JsX.JsWhenMs(event.when))
  }


  def JsPostEvent_apiv0(event: PostEvent): JsObject = {
    Json.obj(
        "id" -> event.id,
        "eventTypes" -> JsArray(event.eventTypes map JsPostEventTypeString_apiv0),
        "when" -> JsX.JsWhenMs(event.when))
  }

}
