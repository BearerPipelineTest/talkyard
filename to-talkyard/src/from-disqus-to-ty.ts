/// <reference path="to-talkyard.d.ts" />

// Docs about the Disqus comments export XML file:
// https://help.disqus.com/developer/comments-export


import * as _ from 'lodash';
import * as sax from 'sax';
import { die, dieIf, logMessage } from '../../tests/e2e/utils/log-and-die';
import c from '../../tests/e2e/test-constants';
import { URL } from 'url';


interface DisqusToTyParams {
  verbose?: Bo;
  primaryOrigin?: St;
  skipLocalhostAndNonStandardPortComments?: Bo;
  convertHttpToHttps?: Bo;
}


/**
 * Categories are for "advanced" bloggers who split their blog comments in
 * different blog topic categories? Skip for now. Maybe some time later,
 * can auto-upsert Disqus categories into Talkyard categories?
 */
interface DisqusCategory {
}


/**
 * There's one Disqus thread per blog post. Each Disqus comment is in one thread.
 * <thread dsq:id="...">....</thread>
 *
 * The <id> is for example:
 *   <id>ghost-7ca248aaa53a3675cc82205g</id>
 * if generated via Ghost, or sometimes just:
 *   <id>ghost-1234</id>.
 * In other cases, looks a bit like an url: (but with a space (!))
 *   <id>1234 https://blogname.com/?p=1234</id>
 * — seems WordPress generates such ids? because I find: "wpengine" and "wordpress.com":
 *   <id>1600 http://blogname1.wpengine.com/?p=1600</id>
 *   <id>189 https://otherblog.wordpress.com/2011/02/03/201102some-thing-html</id>
 * Other <id> samples:
 *   <id>1234</id>  (just a number)
 *   <id>4f322553469275452a2cf27d</id>  (a hex)
 * Often, there's no id:
 *   <id />
 *
 * Currently ignored:
 * <forum>(string)     — all comments need to be from the same Disqus "forum",
 * <category dsq:id="..."/>   — ... and from the same Disqus category, for now.
 */
interface DisqusThread {
  idTag?: string;
  disqusThreadId?: string;
  link?: string;
  title?: string;
  createdAtIsoString?: string;
  author: DisqusAuthor;
  ipAddr?: string;
  isClosed?: boolean;
  isDeleted?: boolean;
  comments: DisqusComment[];
  message?: string;
  // category:  Skip (205AKS5). Mirroring Disqus comments categories to Talkyard seems
  // complicated and no one has asked for that.
}


/**
 * A Disqus comment, represented by a <post> in the Disqus xml.
 *
 * There's a Disqus <id> elem, e.g. <id>wp_id=123</id>, however it's usually
 * empty: <id />. I suppose it's defined only if the comment was imported from
 * WordPress to Disqus, or whatever-else from Disqus, and that Disqus
 * uses it to avoid duplicating comments if importing the same things many times?
 * Just like Talkyard uses extId:s for this.
 *
 * Looking at https://help.disqus.com/en/articles/1717164-comments-export,
 * <parent> can reference <id>. However, in practice, when exporting
 * a Disqus xml dump, <parent> is instead always like: <parent dsq:id="..." />,
 * that is, references the parent comment via dsq:id, not <id>.
 * So, Talkyard looks at the dsq:id attributes but not any contents
 * of <id> or <parent>.
 */
interface DisqusComment {
  idTag?: string;
  // <thread dsq:id="...">
  disqusThreadId?: string;
  // The dsq:id="..." attribute on the <post> itself.
  disqusCommentId?: string;
  // <parent dsq:id="...">
  disqusParentCommentId?: string;
  message?: string;
  createdAtIsoString?: string;
  // Weird: In Disqus XSD, this author elem isn't required. But with no comment
  // author, then, who posted the comment? Seems in practice it's always
  // present though. The XSD: http://disqus.com/api/schemas/1.0/disqus.xsd
  author: DisqusAuthor;
  ipAddr?: string;
  isClosed?: boolean;
  isDeleted?: boolean;
  isSpam?: boolean;

  // Talkyard currently ignores these three. They're in the XSD though.
  // -------------------
  // What's this for? Maybe if a comment got incorrectly flagged as spam,
  // but the blog author reclassified (approved) it as not spam?
  isApproved?: boolean;
  isFlagged?: boolean;
  // What's this? I can only guess.
  isHighlighted?: boolean;
  // -------------------
}


/**
 * <author> in the Disqus xml.
 */
interface DisqusAuthor {
  email?: string;
  name?: string;
  isAnonymous?: boolean;
  username?: string;
  // Talkyard currently ignores this field. It's in the XSD though.
  link?: string; // an URI
}



let depth = 0;
let numCategories = 0;
let curTagName: string;
let parentTagNameDebug: string;

let curCategory: DisqusCategory;
let curThread: DisqusThread;
let curComment: DisqusComment;

const threadsByDisqusId: { [id: string]: DisqusThread } = {};
const commentsByDisqusId: { [id: string]: DisqusComment } = {};

const DisqusThreadSuffix = ':thr';
const DisqusTitleSuffix = ':ttl';
const DisqusBodySuffix = ':bdy';
const DisqusCommentSuffix = ':cmt';
const DisqusAuthorSuffix = ':ath';
const DisqusExtIdSuffix = ':dsq';


let d2tyParams: DisqusToTyParams | U;
let verbose: boolean | undefined;
let primaryOrigin: string | undefined;
let errors = false;

const strict = true; // set to false for html-mode
const parser = sax.parser(strict, {});

function logVerbose(message: string) {
  if (!verbose) return;
  logMessage(message);
}

let lastWasSameLineDot = false;



// Later. If wants to skip some test addresses, e.g. "https://staging.blog.example.com".
// Could be specified via script cmd param.
const originsToSkip: { [origin: string]: Bo } = {
};


// Funny URL —> real URL query param name, or regex URL extractor.
// Background:
// Not often but sometimes people post comments via Google Translate. Then,
// the blog post address from Disqus is 'https://translate.googleusercontent ...'
// but the real blog post address is in a 'u' query param ('u' for URL?).
// Example URL:
//     https://translate.googleusercontent.com/translate_c
//         ?depth=1&hl=de&prev=search&rurl=translate.google.de&sl=en&sp=nmt4
//         &u=https://real-blog-addr.example.com/some-blog-post/
//         &usg=B7LRMEs...PHfa0rj
const originsToEditRegexs: { [origin: string]: St | RegExp } = {
  'https://translate.googleusercontent.com': 'u',   // or?:  /^.*\&u=(https?:[^&]+).*$/
  // ? 'https://webcache.googleusercontent.com'
};


// Nice to see after the import.
const numCommentsPerOrigin: { [origin: string]: Nr } = {};
const statsByPageId: { [pageId: string]: StatsByPageId } = {};
const urlsSkippedNoComments: St[] = [];


interface StatsByPageId {
  numComments: Nr;
  urls: { [url: string]: Nr };
}


parser.onopentag = function (tag: SaxTag) {
  logVerbose(`onopentag '${tag.name}': ${JSON.stringify(tag)}`);
  if (!verbose) {
    process.stdout.write('.');
    lastWasSameLineDot = true;
  }

  depth += 1;
  parentTagNameDebug = curTagName || parentTagNameDebug;
  curTagName = tag.name;
  const anyDisqusId = tag.attributes['dsq:id'];
  let openedThing: DisqusCategory | DisqusThread | DisqusComment;

  switch (tag.name) {
    case 'disqus':
      // The document opening tag. Ignore.
      break;
    case 'category':
      if (depth > 2) {
        // We're in a <thread> or <post>. Since multiple categories isn't supported
        // right now (205AKS5), the category is always the same, so just ignore it.
        return;
      }
      openedThing = curCategory = {};
      ++numCategories;
      dieIf(numCategories > 1,  // (205AKS5)
          "More than one Disqus category found — not supported. [ToTyE503MRTJ63]");
      break;
    case 'thread':
      dieIf(!!curThread, 'ToTyE5W8T205TF');
      if (curComment) {
        // We should be in a <disqus><post><thread>, i.e. depth 3.
        dieIf(depth !== 3, 'ToTyE305MBRDK5');
        curComment.disqusThreadId = anyDisqusId;
      }
      else {
        // We should be in a <disqus><thread>, depth 2.
        dieIf(depth !== 2, 'ToTyE6301WKTS4');
        openedThing = curThread = {
          disqusThreadId: anyDisqusId,
          author: {},
          comments: <DisqusComment[]> [],
        };
      }
      break;
    case 'post':
      // We should now be in a <disqus><post>, i.e. depth 2.
      dieIf(depth !== 2, 'ToTyE6AKST204A');
      dieIf(!!curComment, 'ToTyE7KRTRART24');
      dieIf(!!curThread, 'ToTyE502MBKRG6');
      openedThing = curComment = {
        disqusCommentId: anyDisqusId,
        author: {},
      };
      break;
    case 'parent':
      // We should be in a <disqus><post><parent>, i.e. depth 3.
      dieIf(depth !== 3, 'ToTyE7MTK05RK');
      dieIf(!!curThread, 'ToTyE8AGPSR2K0');
      dieIf(!curComment, 'ToTyE205MBRKDG');
      curComment.disqusParentCommentId = anyDisqusId;
      break;
    case 'message':
    case 'id':
      // We should be in a <disqus><thread><id> or <message>, depth 3,
      // or a <post> instead of a <thread> — Disqus' XSD requires this.
      const what = () => `Unexpected <${tag.name}> in a <${parentTagNameDebug}>`;
      dieIf(depth !== 3, 'ToTyE602RKDJF3');
      dieIf(!curThread && !curComment, 'ToTy306HWJL', what());
      dieIf(!!curThread && !!curComment, 'ToTyE86FKHR6');
      // The tag value is the text inside the tag, and handled by handleText() below.
      break;
  }

  logVerbose(`new thing: ${JSON.stringify(openedThing)}`);
};


parser.oncdata = handleText;
parser.ontext = handleText;


function handleText(textOrCdata: string) {
  logVerbose(`handleText: "${textOrCdata}"`);
  if (curCategory)
    return;
  const commentOrThread = curComment || curThread;
  const author: DisqusAuthor | undefined = commentOrThread ? commentOrThread.author : undefined;
  switch (curTagName) {
    case 'id':
      dieIf(!commentOrThread, 'ToTyE6FKT20XD45');
      commentOrThread.idTag = (commentOrThread.idTag || '') + textOrCdata;
      break;
    case 'link':
      dieIf(!curThread, 'ToTyE20MKDK5');
      curThread.link = textOrCdata;
      break;
    case 'title':
      dieIf(!curThread, 'ToTyE20MK506MSRK5');
      curThread.title = (curThread.title || '') + textOrCdata;
      break;
    case 'message':
      dieIf(!commentOrThread, 'ToTyE6AMBS20NS');
      commentOrThread.message = (commentOrThread.message || '') + textOrCdata;
      break;
    case 'createdAt':
      dieIf(!commentOrThread, 'ToTyE5BSKW05');
      commentOrThread.createdAtIsoString = textOrCdata;
      break;
    case 'ipAddress':
      dieIf(!commentOrThread, 'ToTyE5BMR0256');
      commentOrThread.ipAddr = textOrCdata;
      break;
    case 'email':
      dieIf(!author, 'ToTyE7DMRNJ20');
      author.email = textOrCdata;
      break;
    case 'name':
      dieIf(!author, 'ToTyE5BMRGW02');
      author.name = textOrCdata;
      break;
    case 'username':
      dieIf(!author, 'ToTyE8PMD026Q');
      author.username = textOrCdata;
      break;
    case 'isAnonymous':
      dieIf(!author, 'ToTyE5BFP20ZC');
      author.isAnonymous = textOrCdata === 'true';
      break;
    case 'isDeleted':
      dieIf(!commentOrThread, 'ToTyE7MSSD4');
      commentOrThread.isDeleted = textOrCdata === 'true';
      break;
    case 'isClosed':
      dieIf(!commentOrThread, 'ToTyE4ABMF025');
      commentOrThread.isClosed = textOrCdata === 'true';
      break;
    case 'isSpam':
      dieIf(!curComment, 'ToTyE5MSBWG03');
      curComment.isSpam = textOrCdata === 'true';
      break;
    case 'isApproved':
      dieIf(!curComment, 'ToTyE8FKRCF31');
      curComment.isApproved = textOrCdata === 'true';
      break;
    case 'isFlagged':
      dieIf(!curComment, 'ToTyE2AKRP34U');
      curComment.isFlagged = textOrCdata === 'true';
      break;
    case 'isHighlighted':
      dieIf(!curComment, 'ToTyE9RKP2XZ');
      curComment.isHighlighted = textOrCdata === 'true';
      break;
  }
}


parser.onclosetag = function (tagName: string) {
  depth -= 1;
  logVerbose(`onclosetag: ${tagName}`);
  let closedThing;
  switch (tagName) {
    case 'category':
      // (No effect, if undefined already.)
      closedThing = curCategory;
      curCategory = undefined;
      break;
    case 'thread':
      if (curComment) {
        // This tag tells to which already-creted-thread a post belongs
        // — we shouldn't try to create a new thread here.
        // Example:
        //   <post dsq:id="...">
        //     ...
        //     <thread dsq:id="..." />
        //   </post>
        return;
      }
      dieIf(!curThread, 'ToTyE305MBRS');
      dieIf(!curThread.disqusThreadId, 'ToTyE5BM205');
      threadsByDisqusId[curThread.disqusThreadId] = curThread;
      closedThing = curThread;
      curThread = undefined;
      break;
    case 'post':
      dieIf(!!curThread, 'ToTyE5RD0266');
      dieIf(!curComment, 'ToTyE607MASK53');
      const threadId = curComment.disqusThreadId;
      dieIf(!threadId, 'ToTyE2AMJ037R');
      const thread = threadsByDisqusId[threadId];
      dieIf(!thread,
          `Thread ${threadId} for post ${curComment.disqusCommentId} missing [ToTyE0MJHF56]`);
      thread.comments.push(curComment);
      commentsByDisqusId[curComment.disqusCommentId] = curComment;
      closedThing = curComment;
      curComment = undefined;
      break;
    default:
      // Ignore.
  }
  curTagName = undefined;

  logVerbose(`Closed '${tagName}': ${JSON.stringify(closedThing)}`);
};


parser.onerror = function (error: any) {
  errors = true;
};


parser.onend = function () {
};


function buildTalkyardSite(threadsByDisqusId: { [id: string]: DisqusThread }): any {
  let nextPageId  =  c.LowestTempImpId;
  let nextPostId  =  c.LowestTempImpId;
  let nextGuestId = -c.LowestTempImpId;
  const categoryImpId = c.LowestTempImpId;

  const tySiteData: any = {
    groups: [],
    members: [],
    guests: [],
    pages: [],
    pagePaths: [],
    pageIdsByAltIds: {},
    posts: [],
    categories: [],
    permsOnPages: [],
  };

  // Even if the Disqus user has a real username account, we'll insert
  // it into Talkyard as a guest. Too complicated to find out if
  // hens email has been verified and if someone who logs in to Talkyard
  // with the same email is indeed the same person or not.
  const guestsByImpId: { [guestImpId: string]: GuestDumpV0 } = {};

  Object.keys(threadsByDisqusId).forEach(threadDisqusId => {
    const thread: DisqusThread = threadsByDisqusId[threadDisqusId];

    // Disqus creates threads also for blog posts with no comments; don't import those.
    // Instead, let Talkyard lazy-creates pages when needed.
    if (!thread.comments.length) {
      urlsSkippedNoComments.push(thread.link);
      return;
    }


    // ----- Discussion start date

    // Surprisingly, Disqus can set a thread's (i.e. a blog post discussion's)
    // creation date-time to *after* the first comment got posted (and approved,
    // since the comment was by the blog author henself).
    // Example: (note: the same thread id)
    //  <thread dsq:id="2233445566">
    //     <createdAt>2008-03-12T00:00:00Z</createdAt>
    //     ...
    //  <post>
    //     <createdAt>2008-03-11T17:00:00Z</createdAt>
    //     <thread dsq:id="2233445566" />
    //     ...
    //
    // (Disqus maybe rounded up to the next day? To store a date, not a timestamp?)
    //
    // But Talkyard doesn't allow comments dated before the discussion existed.
    // So, find the oldest date among all comments, and the thread itself,
    // and use that date, as the discussion creation date.

    let pageCreatedAt: WhenMs = Date.parse(thread.createdAtIsoString);

    thread.comments.forEach((comment: DisqusComment) => {
      if (comment.createdAtIsoString) {
        const commentCreatedAt = Date.parse(comment.createdAtIsoString);
        if (commentCreatedAt < pageCreatedAt) {
          pageCreatedAt = commentCreatedAt;
        }
      }
    });


    // ----- The URL

    // Sometimes ignore the whole page. Sometimes change the URL — e.g.
    // extract the real URL if someone loaded & commented on a blog post
    // via Google Translate.

    const urlObjMaybeFunny = new URL(thread.link);
    const funnyUrlRemapper = originsToEditRegexs[urlObjMaybeFunny.origin];
    let notFunnyUrl: St = thread.link;

    if (funnyUrlRemapper) {
      if (lastWasSameLineDot) {  // just for now
        process.stdout.write('\n');
        lastWasSameLineDot = false;
      }
      let betterUrl: St | Nl | U;
      const isQueryParamName = _.isString(funnyUrlRemapper);
      if (isQueryParamName) {
        betterUrl = urlObjMaybeFunny.searchParams.get(funnyUrlRemapper as St);
      }
      else {
        die(`Hmm, regex remapping unimpl:  ${thread.link}`);
        // betterUrl = ...
      }
      console.log(`Remapping URL: ${thread.link}`);
      console.log(`      to this: ${betterUrl}`);
      if (!betterUrl) {
        console.log(` — that's not an URL though. Skipping that "URL".`);
        return;
      }
      notFunnyUrl = betterUrl;
    }

    const urlInclOriginMaybeHttp = notFunnyUrl;
    const urlInclOrigin = d2tyParams.convertHttpToHttps
            ? urlInclOriginMaybeHttp.replace(/^http:/, 'https:')
            : urlInclOriginMaybeHttp;

    const urlObj = new URL(urlInclOrigin);
    const urlOrigin = urlObj.origin;
    const urlPath = urlObj.pathname;
    const urlOriginAndPath = urlOrigin + urlPath;


    if (!!originsToSkip[urlOrigin]) {
      if (lastWasSameLineDot) {  // just for now
        process.stdout.write('\n');
        lastWasSameLineDot = false;
      }
      console.log(`Skipping URL: ${urlInclOrigin}`);
      console.log(`    because ignoring origin: ${urlOrigin}`);
      return;
    }

    // Skip non-standard ports — they were for testing only, typically.
    if (d2tyParams.skipLocalhostAndNonStandardPortComments) {
      const port: St = urlObj.port;
      if (port) {
        if (port === '80' && urlObj.protocol === 'http') {
          // Fine.
        }
        else if (port === '443' && urlObj.protocol === 'https') {
          // Fine.
        }
        else {
          console.log(`Skipping URL: ${urlInclOrigin}`);
          console.log(`    because ignoring non-standard port: ${urlObj.port}`);
          return;
        }
      }
      if (urlObj.hostname === 'localhost') {
        console.log(`Skipping URL: ${urlInclOrigin}`);
        console.log(`    because it's localhost`);
        return;
      }
    }


    // ----- Page

    // Create a Talkyard EmbeddedComments discussion page for this Disqus
    // thread, i.e. blog post with comments.


    // Old, so complicated:
    // The url might be just an origin: https://ex.co, with no trailing slash '/'.
    // urlInclOrigin.replace(/https?:\/\/[^/?&#]+\/?/, '/')  // dupl [305MBKR52]
    //   .replace(/[#?].*$/, '');

    // Find the canonical embedding page URL.
    //
    // Disqus creates different threads for the same page, if the query string
    // is different, e.g.:
    // - https://someblog/blog-post-slug/?ref=example.com
    // - https://someblog/blog-post-slug/?utm_name=something
    // - https://someblog/blog-post-slug/?from=somewhere&amp;isfeature=0
    // Then almost alays, the URL path (or origin + path) uniqueley
    // identifies the blog post. So ignore the query string.
    //
    // COULD add a to-talkyard command line flag for this?
    // --ignoreQueryStringWhenComparingUrls  ?  [more_2ty_cmd_prms]
    //
    // And two identical URL path but with / without a trailing slash, are typically
    // also the same blog post.  Query param for that too?
    // (Currently Ty looks at them as being different, though, just like Disqus does.)
    // --add/removeTrailingSlashWhenComparingUrls  ?  [more_2ty_cmd_prms]
    //
    // Maybe also:   [more_2ty_cmd_prms]
    // --changeOriginsTo https://current-blog-address.example.com
    // --skipOrigins staging.example.com,test.example.com
    // --changeOriginFromTo from.ex.com,to.ex.com
    //
    const canoEmbPageUrl = urlOriginAndPath;  // or: thread.link  [ign_q_st]

    const pageIdToReuse: PageId | U = tySiteData.pageIdsByAltIds[canoEmbPageUrl];
    const pageToReuse: PageDumpV0 | U = _.find(tySiteData.pages,
            (p: PageDumpV0) => p.id === pageIdToReuse);

    if (pageToReuse) {
      console.log(`\nReusing page ${pageToReuse.id
            }, ext id: ${pageToReuse.extImpId
            }, for Disqus thread: "${threadDisqusId
            }",\nwith url: ${thread.link
            },\nbecause same canonical embedding page URL: ${   // [ign_q_st]
                  canoEmbPageUrl}`);
    }

    const pageId: PageId = pageToReuse?.id || (function() {
      const id = '' + nextPageId;
      nextPageId += 1;
      return id;
    })();

    const tyPage: PageDumpV0 = pageToReuse || {
      dbgSrc: 'ToTy',
      id: pageId,
      extImpId: threadDisqusId + DisqusThreadSuffix + DisqusExtIdSuffix,
      pageType: c.TestPageRole.EmbeddedComments,
      version: 1,
      createdAt: pageCreatedAt,
      updatedAt: pageCreatedAt,
      publishedAt: pageCreatedAt,
      categoryId: categoryImpId,
      embeddingPageUrl: canoEmbPageUrl,
      authorId: c.SystemUserId,
    };

    const tyPagePath: PagePathDumpV0 = {
      folder: '/',
      pageId: tyPage.id,
      showId: true,
      slug: 'imported-from-disqus',
      canonical: true,
    };


    // ----- Title and body  [307K740]

    // Disqus doesn't have any title or body post, so we generate our own
    // title and body post.

    let tyTitle: PostDumpV0;
    let tyBody: PostDumpV0;

    if (pageToReuse) {
      // We've already generated a title and page body.
    }
    else {
      tyTitle = {
        id: nextPostId,
        extImpId: threadDisqusId + DisqusThreadSuffix + DisqusTitleSuffix +
              DisqusExtIdSuffix,
        pageId: tyPage.id,
        nr: c.TitleNr,
        postType: PostType.Normal,
        createdAt: pageCreatedAt,
        createdById: c.SystemUserId,
        currRevById: c.SystemUserId,
        currRevStartedAt: pageCreatedAt,
        currRevNr: 1,
        approvedSource: "Comments for " + thread.title,
        approvedAt: pageCreatedAt,
        approvedById: c.SystemUserId,
        approvedRevNr: 1,
      };

      nextPostId += 1;

      tyBody = {
        ...tyTitle,
        id: nextPostId,
        extImpId: threadDisqusId + DisqusThreadSuffix + DisqusBodySuffix +
              DisqusExtIdSuffix,
        nr: c.BodyNr,
        approvedSource: `Comments for <a href="${canoEmbPageUrl}">${canoEmbPageUrl}</a>`,
      };

      nextPostId += 1;
    }


    // ----- Comments and authors

    // Either:
    let nextPostNr = c.LowestTempImpId;

    // Or: If we've added comments to the current page already (can happen if
    // comments were added via Disqus at different URLs, to the in fact same page),
    // find the highest post nr used this far, and continue from there.
    if (pageToReuse) {
      const otherCommentsMaybeSamePage: PostDumpV0[] = tySiteData.posts;
      for (const p of otherCommentsMaybeSamePage) {
        if (p.pageId === tyPage.id && nextPostNr <= p.nr) {
          nextPostNr = p.nr + 1;
        }
      }
    }

    const tyComments: PostDumpV0[] = [];

    thread.comments.forEach((comment: DisqusComment) => {
      const disqParentId = comment.disqusParentCommentId;
      if (disqParentId) {
        const parentComment = commentsByDisqusId[disqParentId];
        dieIf(!parentComment,
          `Cannot find parent comment w Diqus id '${disqParentId}' in all comments [ToTyE2KS70W]`);
        const parentAgain = thread.comments.find(p => p.disqusCommentId === disqParentId);
        dieIf(!parentAgain,
          `Cannot find parent comment w Diqus id '${disqParentId}' in thread [ToTyE50MRXV2]`);
      }

      const disqAuthor = comment.author;

      // Abort if username or email addr contains '|', can otherwise mess up the ext ids
      // and cause duplication (e.g. if a username has '|' in a way that makes it look
      // like:  email-address|is-anonymous|name, which could match a no-username user).
      dieIf(disqAuthor.username && disqAuthor.username.indexOf('|') >= 0,
        `Username contains '|': '${disqAuthor.username}' [ToTyE40WKSTG]`);
      dieIf(disqAuthor.email && disqAuthor.email.indexOf('|') >= 0,
        `Email contains '|': '${disqAuthor.email}' [ToTyE7KAT204ZS]`);
      dieIf(disqAuthor.name && disqAuthor.name.indexOf('|') >= 0,   // (259RT24)
        `Name contains '|': '${disqAuthor.name}' [ToTyE7KAT204Z7]`);

      function makeNoUsernameExtId() {
        // If the email and name are the same, let's assume it's the same person.
        // Ext ids can be any graphical characters (posix: [[:graph:]]), plus, spaces ' '
        // are allowed inside an id, so, using the Disqus comment author names as part
        // of the id, is fine. See db fn  is_valid_ext_id()   [05970KF5].
        return (
            (disqAuthor.email || '')            + '|' +
            (disqAuthor.isAnonymous ? 'a' : '') + '|' +
            (disqAuthor.name || ''));  // maybe later, can contain '|' ?  So place last.
      }                                // but right now, cannot (259RT24)

      const guestExtId =
          (disqAuthor.username || makeNoUsernameExtId()) +
          DisqusAuthorSuffix + DisqusExtIdSuffix;
      const anyDuplGuest = guestsByImpId[guestExtId];
      const anyDuplGuestCreatedAt = anyDuplGuest ? anyDuplGuest.createdAt : undefined;

      const thisGuestId = anyDuplGuest ? anyDuplGuest.id : nextGuestId;

      if (thisGuestId === nextGuestId) {
        // (Guest ids are < 0 so decrement the ids.)
        nextGuestId -= 1;
      }

      const commentCreatedAt = Date.parse(comment.createdAtIsoString);

      const guest: GuestDumpV0 = {
        id: thisGuestId,
        extImpId: guestExtId,  // PRIVACY SHOULD GDPR delete, if deleting user — contains name [03KRP5N2]
        // Use the earliest known post date, as the user's created-at date.
        createdAt: Math.min(anyDuplGuestCreatedAt || Infinity, commentCreatedAt),
        fullName: disqAuthor.name,
        emailAddress: disqAuthor.email,
        // guestBrowserId — there is no such thing in the Disqus xml dump. [494AYDNR]
        //postedFromIp: post.ipAddr
      };

      // If the guest has a username, and has changed hens name or email,
      // this might also change the name or email.
      // COULD remember the most recent email addr and name use that?
      guestsByImpId[guestExtId] = guest;

      const tyPost: PostDumpV0 = {
        id: nextPostId,
        extImpId: comment.disqusCommentId + DisqusCommentSuffix + DisqusExtIdSuffix,
        pageId: tyPage.id,
        nr: nextPostNr,
        parentNr: undefined, // updated below
        postType: PostType.Normal,
        createdAt: commentCreatedAt,
        createdById: guest.id,
        currRevById: guest.id,
        currRevStartedAt: commentCreatedAt,
        currRevNr: 1,
        approvedSource: comment.message,
        approvedAt: commentCreatedAt,
        approvedById: c.SystemUserId,
        approvedRevNr: 1,
      };

      // We need to incl also deleted comments, because people might have replied
      // to them before they got deleted, so they are needed in the replies tree structure.
      if (comment.isDeleted || comment.isSpam) {
        tyPost.deletedAt = commentCreatedAt; // date unknown
        tyPost.deletedById = c.SystemUserId;
        tyPost.deletedStatus = DeletedStatus.SelfBit;  // but not SuccessorsBit
        // Skip this; a db constraint [40HKTPJ] wants either approved source, or a source patch,
        // and it's compliated to construct a patch from any approved source,
        // to the current source?
        //if (comment.isSpam) {
        //  delete tyPost.approvedSource;
        //  delete tyPost.approvedAt;
        //  delete tyPost.approvedById;
        //  delete tyPost.approvedRevNr;
        //}
      }

      nextPostId += 1;
      nextPostNr += 1;

      // Update stats.
      {
        let stats: StatsByPageId = statsByPageId[tyPage.id];
        if (!stats) {
          stats = { numComments: 0, urls: {} };
          statsByPageId[tyPage.id] = stats;
        }
        stats.numComments += 1;
        const urlChanged = canoEmbPageUrl !== thread.link;
        const statsKey = canoEmbPageUrl + (urlChanged ? `  was: ${thread.link}` : '');
        const numCmtsCurUrl = stats.urls[statsKey] || 0;
        stats.urls[statsKey] = numCmtsCurUrl + 1;

        const numCmtsThisOrigin = numCommentsPerOrigin[urlOrigin] || 0;
        numCommentsPerOrigin[urlOrigin] = numCmtsThisOrigin + 1;
      }

      tyComments.push(tyPost);
    });


    // ----- Fill in parent post nrs

    tyComments.forEach(tyComment => {
      const suffixLength = DisqusCommentSuffix.length + DisqusExtIdSuffix.length;
      const disqusId: string = tyComment.extImpId.slice(0, - suffixLength);
      const disqusComment: DisqusComment = commentsByDisqusId[disqusId];
      dieIf(!disqusComment, 'ToTyE305DMRTK6');
      const disqusParentId = disqusComment.disqusParentCommentId;
      if (disqusParentId) {
        const disqusParent = commentsByDisqusId[disqusParentId];
        dieIf(!disqusParent,
            `Parent Disqus comment not found, Disqus id: '${disqusParentId}' ` +
            `[ToTyEDSQ0DSQPRNT]`);
        const parentExtId = disqusParentId + DisqusCommentSuffix + DisqusExtIdSuffix;
        const tyParent = tyComments.find(c => c.extImpId === parentExtId);
        dieIf(!tyParent,
            `Parent of Talkyard post nr ${tyComment.nr} w Disqus id '${disqusId}' not found, ` +
            `parent's ext id: '${parentExtId}' ` +
            '[ToTyEDSQ0PRNT]');
        tyComment.parentNr = tyParent.nr;
      }
    });


    // ----- Add to site

    logVerbose(`Adding discussion at: '${urlInclOrigin}', url path '${urlPath}', ` +
        `with ${thread.comments.length} comments, ` +
        `to Talkyard page with temp imp id ${tyPage.id} ...`);
    if (!verbose) {
      process.stdout.write('.');
      lastWasSameLineDot = true;
    }

    if (!pageToReuse) {
      tySiteData.pages.push(tyPage);
      tySiteData.pagePaths.push(tyPagePath);
    }
    // Else: We've done that already.


    // Map discussion id to page:  [docs-3035KSSD2]
    //
    // ("Thread" id, in Disqus terminology.)
    // These ids can be a bit weird, e.g. include spaces. They might have been
    // generated by WordPress, or Ghost, or whatever else, [52TKRG40]
    // and later imported into Disqus — and now imported from Disqus to Talkyard.
    // Prefix with 'diid:' so the ids get their own namespace, and won't
    // be mistaken for embedding URLs and URL paths (which Talkyard stores
    // in the same db table).

    if (thread.idTag) {
      tySiteData.pageIdsByAltIds['diid:' + thread.idTag] = tyPage.id;
    }


    // Map full URL to page:

    // This cannot happen? Disqus never maps the same full URL to different threads.
    const pageIdByUrlInclOrig = tySiteData.pageIdsByAltIds[urlInclOrigin];
    dieIf(pageIdByUrlInclOrig && pageIdByUrlInclOrig !== tyPage.id,
        `Full URL ${urlInclOrigin} maps to both tyPage.id ${pageIdByUrlInclOrig} ` +
        `and ${tyPage.id} [ToTyEDUPLURL]\n\n` +
        `Disqus thread:\n${JSON.stringify(thread, undefined, 4)}\n\n` +
        `First dupl page:\n${JSON.stringify(
              _.find(tySiteData.pages, p => p.id === pageIdByUrlInclOrig),
                                            undefined, 4)}\n\n` +
        `Second dupl page:\n${JSON.stringify(tyPage, undefined, 4)}\n\n`);

    tySiteData.pageIdsByAltIds[urlInclOrigin] = tyPage.id;


    // Map URL path to page too:

    // So comments are found, also if the blog moves to a new domain. [TyT205AKST35]

    // Tricky: Paths to two *different* discussions can be the same, if a
    // Disqus export file has comments for blog two
    // posts on different domains, but with the same url path. Then, typically,
    // it's the same blog, just that it's been hosted on different domains, and
    // people posted comments on the first domain, creating a Disqus thread there,
    // and then on the 2nd domain, creating a duplicated thread for the in fact
    // same blog post, there.
    // For now, if this happens let's require the human to choose one of
    // the dommains, via --primaryOrigin. Later, there could 1) be advanced
    // params to merge the different (duplicated?) discussions together to
    // one single discussion (so no comments are lost). Or 2)
    // the human could use Talkard's interface later, to move everything
    // to the same page. Maybe there could be an embedded discussions pages
    // list that helps with identfying these problems? (shows dupl / similar urls
    // for separate pages that should *probably* be the same?)
    //
    // Cmd param: --changeOriginFromTo from.ex.com,to.ex.com   ?  [more_2ty_cmd_prms]
    //
    const pageIdByUrlPath: PageId | U = tySiteData.pageIdsByAltIds[urlPath];
    let skipPath = false;
    if (pageIdByUrlPath && pageIdByUrlPath !== tyPage.id) {
      if (primaryOrigin) {
        // Overwrite the existing map entry, iff this is the primary origin.
        const notPrimary = !urlInclOrigin.startsWith(primaryOrigin + '/') &&
                urlInclOrigin != primaryOrigin;
        skipPath = notPrimary;
      }
      else {
        // Log error, show other URLS with same path, but different origins.
        const otherSimilarUrls = _.filter(
            _.keys(tySiteData.pageIdsByAltIds), url => {
              if (!url.startsWith('http:') && !url.startsWith('https:'))
                return false;
              if (url === urlInclOrigin)
                return false;
              const urlObj = new URL(url);
              return urlObj.pathname === urlPath;
            });
        // TESTS_MISSING
        die(`URL path '${urlPath}' maps to both tyPage.id ${pageIdByUrlPath} ` +
            `and ${tyPage.id}. Maybe 1) your Disqus XML file includes blog posts ` +
            `from different domains, but with the same URL path? ` +
            `Or 2) Disqus has saved comments for this specific blog post, but used ` +
            `different URLs and different Disqus thread (blog post) ids? ` +
            `I'm looking at this URL: '${urlInclOrigin}'` +
            (urlInclOrigin === thread.link ? ', ' :
                  ` (originally: '${thread.link}', was remapped), `) +
            `and previous similar urls I've seen are: ${
                  JSON.stringify(otherSimilarUrls)} —` +
            `note that they end with the same URL path. ` +
            `To solve this, add --primaryOrigin https://one.of.your.blog.addresses, ` +
            `to the command line options, and then I'll use the Disqus comments ` +
            `from that origin, whenever the same URL path maps to ` +
            `different discussions from different domains. [ToTyEDUPLPATH]`);
            // Or just remap other old origins to the current origin?  [more_2ty_cmd_prms]
      }
    }
    if (!skipPath) {
      tySiteData.pageIdsByAltIds[urlPath] = tyPage.id;
    }

    // Unless we've already added the title & body (many Disqus threads for the
    // same blog post), do now.
    dieIf(!!tyTitle === !!pageToReuse, 'TyE503RKGJ2');
    dieIf(!!tyBody === !!pageToReuse, 'TyE503RKGJ3');
    if (!pageToReuse) {
      tySiteData.posts.push(tyTitle);
      tySiteData.posts.push(tyBody);
    }

    // We shouldn't be doing this at all, if no comments for this page (Disqus thread).
    dieIf(!tyComments.length, 'TyE503RKGJ4');
    tyComments.forEach(c => tySiteData.posts.push(c));
  });

  _.values(guestsByImpId).forEach(g => tySiteData.guests.push(g));

  // A dummy category that maps the category import id to [the category
  // in the database with ext id 'embedded_comments'].
  tySiteData.categories.push({
    id: categoryImpId,
    extId: 'embedded_comments',
  });

  return tySiteData;
}


export default function(fileText: string, ps: DisqusToTyParams): [SiteData, boolean] {
  d2tyParams = ps;
  verbose = ps.verbose;
  primaryOrigin = ps.primaryOrigin;

  console.log("Parsing ...");
  parser.write(fileText).close(); // this updates threadsByDisqusId

  console.log("\nDone parsing. Converting to Talkyard JSON ...");
  const site = buildTalkyardSite(threadsByDisqusId);

  console.log("\nDone converting to Talkyard.");

  console.log(`\n\nURLs skipped, no comments:\n\n${
        JSON.stringify(urlsSkippedNoComments, undefined, 4)}`);

  console.log(`\n\nStats by page id:\n\n${
        JSON.stringify(statsByPageId, undefined, 4)}`);

  console.log(`\n\nStats by origin:\n\n${
        JSON.stringify(numCommentsPerOrigin, undefined, 4)}`);

  return [site, errors];
}

