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

trait EventType


// RENAME to SwEvent or TyEvent? sw = software. But not an IRL (in-real-life) meetup event
// Or let Event be a software event, and Happening or Meeting or RealEvent or IrlEvent be
// an in-real-life event?  ... No. "Event" sort of 'always' refers to a webhook / event log
// event, in software like Ty. But an IRL event could be IrlEvent?
sealed abstract class Event {
  def id: EventId
  def when: When
  def eventType: EventType
}



sealed abstract class PageEventType extends EventType

object PageEventType {
  case object PageCreated extends PageEventType
  case object PageUpdated extends PageEventType

  /*
  case object PageDeleted extends PageEventType
  case object PageUndeleted extends PageEventType

  case object PageClosed extends PageEventType
  case object PageReopened extends PageEventType

  case object PageAnswered extends PageEventType
  //case object PageUnanswered extends PageEventType

  case object PageDone extends PageEventType
  //case object PageUnplanned extends PageEventType
 */
}


case class PageEvent(
  when: When,
  eventType: PageEventType,
  // subTypes: ImmSeq[PageEventSubType],
  underlying: AuditLogEntry) extends Event {

  def id: EventId = underlying.id

  def pageId: PageId = underlying.pageId getOrDie s"Page event $id has no page id [TyEEV0PAGEID]"
}



sealed abstract class PostEventType extends EventType

object PostEventType {
  case object PostCreated extends PostEventType
  case object PostUpdated extends PostEventType
}


case class PostEvent(
  when: When,
  eventType: PostEventType,
  //subTypes: ImmSeq[PostEventType],
  underlying: AuditLogEntry) extends Event {

  def id: EventId = underlying.id

  def postId: PostId = underlying.uniquePostId getOrDie s"Post event $id has no post id [TyEEV0POSTID]"
}



object Event {

  /** One log line can affect many pages (e.g. moving a comment from one to another)
    * so, returns a list of events.
    */
  def fromAuditLogItem(logEntry: AuditLogEntry): Opt[Event] = {
    val when = When.fromDate(logEntry.doneAt)

    val postEventType: Opt[PostEventType] = logEntry.didWhat match {
      case AuditLogEntryType.NewChatMessage | AuditLogEntryType.NewReply =>
        Some(PostEventType.PostCreated)
      case AuditLogEntryType.EditPost =>
        Some(PostEventType.PostUpdated)
      case _ =>
        None
    }

    postEventType foreach { t =>
      return Some(PostEvent(when = when, t, logEntry))
    }

    val PET = PageEventType

    val pageEventType: Opt[PageEventType] = logEntry.didWhat match {
      case AuditLogEntryType.NewPage =>
        Some(PET.PageCreated)

      case AuditLogEntryType.PageAnswered
         | AuditLogEntryType.PageDone
         | AuditLogEntryType.PageAnswered
         | AuditLogEntryType.PageClosed
         | AuditLogEntryType.PageOpened
         | AuditLogEntryType.DeletePage
         | AuditLogEntryType.UndeletePage =>
        Some(PET.PageUpdated)

      /*
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
       */

      case _ =>
        None
    }

    pageEventType foreach { t =>
      return Some(PageEvent(when = when, t, logEntry))
    }

    None

    /*
    val resultBuilder = Vec.newBuilder[Event]

    if (postEventType.isDefined) {
      resultBuilder += PostEvent(when = when, postEventType, logEntry)
    }

    if (pageEventType.isDefined) {
      resultBuilder += PageEvent(when = when, pageEventType, logEntry)
    }

    resultBuilder.result
     */
  }
}