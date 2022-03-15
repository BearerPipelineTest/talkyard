package talkyard.server.parser

import com.debiki.core._
import talkyard.server.JsX
import debiki.dao.{LoadPostsResult, PageStuff, SiteDao}

import play.api.libs.json._

case class EventAndJson(event: Event, json: JsObject)

// MOVE to pkg events?
object EventsParSer {



  def makeEventsListJson(events: ImmSeq[Event], dao: SiteDao, reqer: Opt[Pat],
          avatarUrlPrefix: St): ImmSeq[EventAndJson] = {

    val (postsById, //: Map[PostId, Post],
          origPostsByPageId, //: Map[PageId, Post],
          pagesById) = { //: Map[PageId, PageStuff]) = {

      val pagePostNrs: ImmSeq[PagePostNr] = events collect {
        case ev: PageEvent if ev.eventType == PageEventType.PageCreated =>
          PagePostNr(ev.pageId, BodyNr)
      }

      val otherPageIds: MutArrBuf[PageId] = (events collect {
        case ev: PageEvent if ev.eventType != PageEventType.PageCreated =>
          ev.pageId
      }).to[MutArrBuf]

      val postIds: ImmSeq[PostId] = events collect {
        case ev: PostEvent => ev.postId
      }

      val postsById = MutHashMap[PostId, Post]()
      val origPostsByPageId = MutHashMap[PageId, Post]()
      val pageStuffById = MutHashMap[PageId, PageStuff]()

      val loadPostsByIdResult: LoadPostsResult = dao.loadPostsMaySeeByIdNrs(
            reqer, postIds = Some(postIds), pagePostNrs = None)
      loadPostsByIdResult.posts foreach { p =>
        postsById += p.id -> p
        otherPageIds append p.pageId
      }

      val loadPostsByPageNrResult: LoadPostsResult = dao.loadPostsMaySeeByIdNrs(
            reqer, postIds = None, pagePostNrs = Some(pagePostNrs))

      loadPostsByPageNrResult.posts foreach { p => origPostsByPageId += p.pageId -> p }
      pageStuffById ++= loadPostsByPageNrResult.pageStuffById

      val pageStuffByIdInclForbidden: Map[PageId, PageStuff] =
            dao.getPageStuffById(otherPageIds)

      pageStuffByIdInclForbidden.foreach({ case (_, pageStuff: PageStuff) =>
        if (dao.maySeePageUseCache(pageStuff.pageMeta, reqer)._1) {
          pageStuffById += pageStuff.pageId -> pageStuff
        }
      })

      //postsById.appendAll(loadPostsByPageNrResult.posts)

      (postsById,  origPostsByPageId, pageStuffById)
    }

    //maySee = maySeePostUseCache(
    //     post, pageMeta, ppt = reqer, maySeeUnlistedPages = inclUnlistedPagePosts)
    //val loadPostsByPageNrResult: LoadPostsResult = dao.loadPageStuffById()
          //reqer, postIds = None, pagePostNrs = Some(pagePostNrs))

    // --- Load categories

    val catIdsToLoad: Set[CatId] = pagesById.values.flatMap(_.pageMeta.categoryId).toSet
    val catsById: Map[CatId, Cat] = dao.getCatsById(catIdsToLoad)

    // --- Load authors

    val authorIds = MutHashSet[PatId]() // postsById(_.createdById).toSet
    authorIds ++= postsById.values.map(_.createdById)
    authorIds ++= origPostsByPageId.values.map(_.createdById)
    authorIds ++= pagesById.values.map(_.authorUserId)

    val authorsById: Map[UserId, Participant] = dao.getParticipantsAsMap(authorIds)

    val pagePaths = pagesById.values.flatMap(p => dao.getPagePath2(p.pageId)).toSeq
    val pagePathsById: Map[PageId, PagePathWithId] = Map(pagePaths.map(p => p.pageId -> p): _*)

    val eventsJson = events flatMap {
      case pageEvent: PageEvent if pageEvent.eventType == PageEventType.PageCreated =>
        for {
          page: PageStuff <- pagesById.get(pageEvent.pageId)
          post: Post <- origPostsByPageId.get(pageEvent.pageId)
          cat: Opt[Cat] = page.categoryId flatMap catsById.get
        }
        yield {
          val json = JsPageEvent_apiv0(pageEvent, page, cat, origPost = Some(post),
                pagePathsById, authorsById, avatarUrlPrefix = avatarUrlPrefix)
          EventAndJson(pageEvent, json)
        }

      case pageEvent: PageEvent if pageEvent.eventType == PageEventType.PageUpdated =>
        for {
          page: PageStuff <- pagesById.get(pageEvent.pageId)
          cat: Opt[Cat] = page.categoryId flatMap catsById.get
        }
        yield {
          val json = JsPageEvent_apiv0(pageEvent, page, cat, origPost = None,
                pagePathsById, authorsById, avatarUrlPrefix = avatarUrlPrefix)
          EventAndJson(pageEvent, json)
        }

      case postEvent: PostEvent =>
        for {
          post: Post <- postsById.get(postEvent.postId)
          page: PageStuff <- pagesById.get(post.pageId)
        }
        yield {
          val json = JsPostEvent_apiv0(postEvent, post, page, authorsById,
                avatarUrlPrefix = avatarUrlPrefix)
          EventAndJson(postEvent, json)
        }
    }

    eventsJson
  }



  def JsPageEventTypeString_apiv0(eventType: PageEventType): JsString = {
    val ET = PageEventType
    val asSt = eventType match {
      case ET.PageCreated => "PageCreated"
      case ET.PageUpdated => "PageUpdated"

      /*
      case ET.PageDeleted => "Page.Deleted"
      case ET.PageUndeleted => "Page.Undeleted"

      case ET.PageClosed => "Page.Closed"
      case ET.PageReopened => "Page.Reopened"

      case ET.PageAnswered => "Page.Answered"
      //case ET.PageUnanswered

      case ET.PageDone => "Page.Done"
     */
    }

    assert(asSt startsWith "Page")
    JsString(asSt)
  }



  def JsPostEventTypeString_apiv0(eventType: PostEventType): JsString = {
    val ET = PostEventType
    val asSt = eventType match {
      case ET.PostCreated => "PostCreated"
      case ET.PostUpdated => "PostUpdated"
    }

    assert(asSt startsWith "Post")
    JsString(asSt)
  }



  def JsPageEvent_apiv0(event: PageEvent, page: PageStuff, cat: Opt[Cat],
          origPost: Opt[Post], pagePathsById: Map[PageId, PagePathWithId],
          authorsById: Map[PatId, Pat], avatarUrlPrefix: St): JsObject = {
    import talkyard.server.api.ThingsFoundJson
    import talkyard.server.api.PostsListFoundJson.JsPostListFound

    val pageFoundStuff = new ThingsFoundJson.PageFoundStuff(
          pagePath = pagePathsById.getOrElse(page.pageId, PagePathWithId.fromIdOnly(page.pageId,
              // Since there's no other path, this is the canonical one?
              canonical = true)),
          pageStuff = page,
          pageAndSearchHits = None)

    var pageJson = ThingsFoundJson.JsPageFound(
        pageFoundStuff,
        authorIdsByPostId = Map.empty, // only needed for search hits
        authorsById = authorsById,
        avatarUrlPrefix = avatarUrlPrefix,
        anyCategory = cat,
        JsonConf.v0_1)

    origPost foreach { op =>
      val origPostJson = JsPostListFound(
            op, page, authorsById, avatarUrlPrefix = avatarUrlPrefix, JsonConf.v0_1)
      pageJson += "postsByNr" -> Json.obj(BodyNrSt -> origPostJson)
    }

    Json.obj(
        "id" -> event.id,
        "when" -> JsX.JsWhenMs(event.when),
        "type" -> JsPageEventTypeString_apiv0(event.eventType),
        "data" -> Json.obj(
          "page" -> pageJson
        ))
  }



  def JsPostEvent_apiv0(event: PostEvent, post: Post, page: PageStuff,
        authorsById: Map[PatId, Pat], avatarUrlPrefix: St): JsObject = {
    import talkyard.server.api.PostsListFoundJson.JsPostListFound
    val postJson = JsPostListFound(post, page, authorsById, avatarUrlPrefix = avatarUrlPrefix)
    Json.obj(
        "id" -> event.id,
        "when" -> JsX.JsWhenMs(event.when),
        "type" -> JsPostEventTypeString_apiv0(event.eventType),
        "data" -> Json.obj(
          "post" -> postJson
        ))
  }

}
