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


trait EventType

sealed abstract class Event {
  def id: EventId
  def when: When
  def eventTypes: ImmSeq[EventType]
}



sealed abstract class PageEventType extends EventType

object PageEventType {
  case object PageCreated extends PageEventType

  case object PageDeleted extends PageEventType
  case object PageUndeleted extends PageEventType

  case object PageClosed extends PageEventType
  case object PageReopened extends PageEventType

  case object PageAnswered extends PageEventType
  //case object PageUnanswered extends PageEventType

  case object PageDone extends PageEventType
  //case object PageUnplanned extends PageEventType
}


case class PageEvent(
  when: When,
  eventTypes: ImmSeq[PageEventType],
  underlying: AuditLogEntry) extends Event {

  def id: EventId = underlying.id
}



sealed abstract class PostEventType extends EventType

object PostEventType {
  case object PostCreated extends PostEventType
  case object PostEdited extends PostEventType

  // If it's a reply, not an Orig Post.
  case object CommentCreated extends PostEventType
}


case class PostEvent(
  when: When,
  eventTypes: ImmSeq[PostEventType],
  underlying: AuditLogEntry) extends Event {

  def id: EventId = underlying.id
}



object Event {

  /** One log line can affect many pages (e.g. moving a comment from one to another)
    * so, returns a list of events.
    */
  def fromAuditLogItem(logEntry: AuditLogEntry): ImmSeq[Event] = {
    val when = When.fromDate(logEntry.doneAt)

    val postEventTypes: Vec[PostEventType] = logEntry.didWhat match {
      case AuditLogEntryType.NewChatMessage | AuditLogEntryType.NewReply =>
        Vec(PostEventType.PostCreated, PostEventType.CommentCreated)
      case AuditLogEntryType.EditPost =>
        Vec(PostEventType.PostEdited)
      case _ =>
        Vec()
    }

    val PET = PageEventType

    val pageEventTypes: Vec[PageEventType] = logEntry.didWhat match {
      case AuditLogEntryType.NewPage =>
        Vec(PET.PageCreated)

      case AuditLogEntryType.PageAnswered =>
        Vec(PET.PageClosed, PET.PageAnswered)

      //case AuditLogEntryType.PagePlanned =>
      //case AuditLogEntryType.PageStarted => ???

      case AuditLogEntryType.PageDone =>
        Vec(PET.PageClosed, PET.PageDone)

      case AuditLogEntryType.PageClosed =>
        Vec(PET.PageClosed)
      case AuditLogEntryType.PageOpened =>
        Vec(PET.PageReopened)

      case AuditLogEntryType.DeletePage =>
        Vec(PET.PageDeleted)
      case AuditLogEntryType.UndeletePage =>
        Vec(PET.PageUndeleted)

      case _ =>
        Vec()
    }

    val resultBuilder = Vec.newBuilder[Event]

    if (postEventTypes.nonEmpty) {
      resultBuilder += PostEvent(when = when, postEventTypes, logEntry)
    }

    if (pageEventTypes.nonEmpty) {
      resultBuilder += PageEvent(when = when, pageEventTypes, logEntry)
    }

    resultBuilder.result
  }
}