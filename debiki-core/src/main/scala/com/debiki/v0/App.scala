// vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list

package com.debiki.v0

import org.yaml.{snakeyaml => y}
import io._
import scala.collection.JavaConversions._
import scala.collection.{mutable => mut}
import java.{io => jio}
import Prelude._

/** Converts a debate from YAML to HTML.
 */
private[debiki] object App {

  def main(args: Array[String]) {
    var dir = ""
    var out = "-"
    var widths = List[Int]()
    var i = 0
    while (i < args.length) {
      args(i) match {
        case a =>
            // Option value required for these args
            if (i + 1 >= args.length)
              error("Value missing for option: "+ a)
            a match {
              case "-d" =>
                dir = args(i+1)
              case "-o" =>
                out = args(i+1)
                if (!out.endsWith("/")) out = out + '/'
              case "-w" =>
                widths = args(i+1).split(",").toList.map(_.toInt)
              case _ =>
                error("Bad option: "+ a)
            }
            i += 1
      }
      i += 1
    }

    val debate: Debate = DebikiYaml().loadDebateFromPath(dir).open_!
    val layoutMgr = new LayoutManager(debate)

    createDirTree(out, debate)
    copyResources(out)
    writeDebateHtml(debate, out, layoutMgr)
    for (post <- debate.posts)//WithEditProposals)
      writeEditProposalsHtml(post.id, debate, layoutMgr, out)
  }

  private def writeDebateHtml(
      debate: Debate, out: String, layoutMgr: LayoutManager) {
    val xml =
      <html xmlns="http://www.w3.org/1999/xhtml"
        xmlns:lift="http://liftweb.net/">
        <head>
          <title>Test mock layout</title>
          <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
          {/* svg.js must be first js, says svgweb docs.
          <meta name="svg.render.forceflash" content="true" /> */}
          <script type='text/javascript' src='js/svg.js' data-path='js'/>
          {
            for (f0 <- resourceFiles; f = f0.stripPrefix(serveDir)) yield
              if (f endsWith ".css")
                <link type="text/css" rel="stylesheet" href={f}/>
              else if (f.endsWith(".js") && !f.endsWith("/svg.js"))
                <script type="text/javascript" src={f}/>
          }
          <script type="text/javascript">
            jQuery.noConflict()(function($){{
              var selectors =
                '.dw-res, .dw-t, .dw-p, .dw-p-bdy, '+
                '.dw-a, .dw-fs';
              $('body').debiki_dragscrollable({{
                  dragSelector: selectors, scrollable: selectors }});
            }});
          </script>
        </head>
        <body>
          { layoutMgr.layoutDebate() }
        </body>
      </html>

    val html = "<!DOCTYPE html>\n" + xml
    val writer = new jio.FileWriter(out + debate.id + ".html")
    writer.write(html.toString)
    writer.close
  }

  private def writeEditProposalsHtml(postId: String, debate:
      Debate, layout: LayoutManager, out: String) {
    val xml =
      <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <title>Edit suggestions</title>
          <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        </head>
        <body>
        { layout.editForm(postId) }
        </body>
      </html>
    val editPropsDir = out + debate.id +'/'+ Paths.EditsProposed
    new jio.File(editPropsDir).mkdirs()
    val writer = new jio.FileWriter(editPropsDir + postId +".html")
    writer.write("<!DOCTYPE html>\n" + xml.toString)
    writer.close
  }

  val serveDir = "toserve/"

  /** All css and javascript files.
   */
  private val resourceFiles = List(
      "css/debiki/jquery-ui-1.8.4.custom.css",
      "css/debiki.css",
      "css/img/dw-svg-fake-hcurve.png",
      "css/img/dw-svg-fake-hcurve-start.png",
      "css/img/dw-svg-fake-hcurve-start-w-reply-arrow.png",
      "css/img/dw-svg-fake-vcurve-short.png",
      "0/js/diff_match_patch.js",
      "js/svg.js",
      "js/svg.htc",
      "js/svg.swf",
      "js/jquery-1.4.2.js",
      "js/debiki-jquery-ui-1.8.4.custom.min.js",
      "js/jquery.cookie.js",
      "js/debiki-dragscrollable.js",
      "js/debiki.js",
      "js/debiki-layout.js") map (serveDir + _)

  /** Image files, currently jQuery UI images only.
   *  Perhaps I can list the contents of a Jar directory like so:
   *    // Load the directory as a resource
   *    URL dir_url = ClassLoader.getSystemResource(dir_path);
   *    // Turn the resource into a File object
   *    File dir = new File(dir_url.toURI());
   *    // List the directory
   *    String files = dir.list()
   *
   *  See: http://stackoverflow.com/questions/676097/java-resource-as-file
   *  Instead of this hard coded list, which I'll need to update
   *  when jQuery UI adds new files:
   */
  private val imageFiles = List(
      "ui-bg_diagonals-thick_18_b81900_40x40.png",
      "ui-bg_diagonals-thick_20_666666_40x40.png",
      "ui-bg_flat_10_000000_40x100.png",
      "ui-bg_flat_100_f6f6f6_40x100.png",
      "ui-bg_glass_100_fdf5ce_1x400.png",
      "ui-bg_glass_100_fefefe_1x400.png",
      "ui-bg_glass_65_ffffff_1x400.png",
      "ui-bg_highlight-soft_35_ededed_1x100.png",
      "ui-bg_highlight-soft_75_ffe45c_1x100.png",
      "ui-icons_0c93ca_256x240.png",
      "ui-icons_228ef1_256x240.png",
      "ui-icons_238ed7_256x240.png",
      "ui-icons_e9911c_256x240.png",
      "ui-icons_ef8c08_256x240.png",
      "ui-icons_ffd27a_256x240.png") map (serveDir +"css/debiki/images/"+ _)

  /** Creates directories into which css and javascript files will be placed.
   */
  private def createDirTree(dir: String, debate: Debate) {
    val root = new jio.File(dir)
    if (!root.exists) {
      // (not mkdirs, that'd be somewhat unsafe in case of a typo when
      // the user specifies `dir' on the command line?)
      if (!root.mkdir())
        illegalArg("Please create the parent directory of: "+ dir)
    }
    else if (!root.isDirectory)
      illegalArg("Not a directory: "+ dir)
    new jio.File(dir +"0/js/").mkdirs()
    new jio.File(dir +"0/css/").mkdir()
    new jio.File(dir +"js/").mkdir()
    new jio.File(dir +"css/img/").mkdirs()
    new jio.File(dir +"css/debiki/images/").mkdirs()
    new jio.File(dir + debate.id +"/edits/proposed/post/").mkdirs()
  }

  /** Copies javascript, css files and images to folders in `dir'.
   */
  private def copyResources(dir: String) {
    val loader = getClass.getClassLoader
    for (res <- resourceFiles ::: imageFiles) {
      val inputStream = loader.getResourceAsStream(res)
      errorIf(inputStream eq null, "Not found: "+ res)
      copy(fromStream = inputStream,
           toPath = dir +"/"+ res.stripPrefix(serveDir))
      inputStream.close()
    }
  }

  /** Copies the stream to a file; works also for binary files.
   *  Could also use Apache file utils:
   *  http://commons.apache.org/
   *                   io/api-release/org/apache/commons/io/FileUtils.html
   *  but can't have such a dependency.
   *
   *  Caller closes `fromStream'.
   */
  private def copy(fromStream: jio.InputStream, toPath: String) {
    var ostream: jio.FileOutputStream = null
    try {
      ostream = new jio.FileOutputStream(toPath)
      val buffer = new Array[Byte](4096)
      var bytesRead: Int = fromStream.read(buffer)
      while (bytesRead != -1) {
        ostream.write(buffer, 0, bytesRead)
        bytesRead = fromStream.read(buffer)
      }
    }
    finally {
      // caller closes `from'.
      if (ostream != null) ostream.close()
    }
  }

}
