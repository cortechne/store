package functional

import org.joda.time.format.DateTimeFormat
import SeleniumHelpers.FirefoxJa
import play.api.http.Status
import org.openqa.selenium.By
import java.nio.file.{Paths, Files}
import org.openqa.selenium.JavascriptExecutor
import java.util.concurrent.TimeUnit
import helpers.UrlHelper
import helpers.UrlHelper._
import anorm._
import play.api.test.Helpers._
import play.api.Play.current
import helpers.Helper._
import models._
import org.joda.time.format.DateTimeFormat
import play.api.db.DB
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer, FakeApplication}
import play.api.i18n.{Lang, Messages}
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class NewsMaintenanceSpec extends Specification {
  val testDir = Files.createTempDirectory(null)
  lazy val withTempDir = Map(
    "news.picture.path" -> testDir.toFile.getAbsolutePath,
    "news.picture.fortest" -> true
  )

  lazy val avoidLogin = Map(
    "need.authentication.entirely" -> false
  )

  "News maintenace" should {
    "Create news" in {
      val app = FakeApplication(additionalConfiguration = inMemoryDatabase() ++ withTempDir ++ avoidLogin)
      running(TestServer(3333, app), SeleniumHelpers.webDriver(Helpers.FIREFOX)) { browser => DB.withConnection { implicit conn =>
        implicit val lang = Lang("ja")
        val adminUser = loginWithTestUser(browser)
        val site1 = Site.createNew(LocaleInfo.Ja, "商店111")
        val site2 = Site.createNew(LocaleInfo.Ja, "商店222")

        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsMaintenance.startCreateNews().url.addParm("lang", lang.code)
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".globalErrorMessage").getText === Messages("inputError")
        browser.find("#title_field dd.error").getText === Messages("error.required")
        browser.find("#newsContents_field dd.error").getText === Messages("error.required")
        browser.find("#releaseDateTextBox_field dd.error").getText === Messages("error.date")

        browser.fill("#title").`with`("title01")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents01');")
        browser.fill("#releaseDateTextBox").`with`("2016年01月02日")
        browser.find("#siteDropDown option[value='1001']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").getText === Messages("newsIsCreated")

        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code)
        )
        browser.title === Messages("commonTitle", Messages("editNewsTitle"))
        browser.find(".newsTableBody .title").getText === "title01"
        browser.find(".newsTableBody .releaseTime").getText === "2016年01月02日"
        browser.find(".newsTableBody .site").getText === "商店222"
        browser.find(".newsTableBody .id a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("modifyNewsTitle"))
        browser.find("#title").getAttribute("value") === "title01"
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("return tinyMCE.activeEditor.getContent();") === "<p>Contents01</p>"
        browser.find("#siteDropDown option[selected='selected']").getText === "商店222"
        browser.find("#releaseDateTextBox").getAttribute("value") === "2016年01月02日"

        browser.webDriver
          .findElement(By.id("newsPictureUpload0"))
          .sendKeys(Paths.get("testdata/kinseimaruIdx.jpg").toFile.getAbsolutePath)
        val now = System.currentTimeMillis
        browser.click("#newsPictureUploadSubmit0")

        val id = browser.find("#idValue").getAttribute("value").toLong
        testDir.resolve(id + "_0.jpg").toFile.exists === true
        downloadBytes(
          Some(now - 1000),
          "http://localhost:3333" + controllers.routes.NewsPictures.getPicture(id, 0).url
        )._1 === Status.OK

        downloadBytes(
          Some(now + 10000),
          "http://localhost:3333" + controllers.routes.NewsPictures.getPicture(id, 0).url
        )._1 === Status.NOT_MODIFIED

        // Delete file.
        browser.click("#newsPictureRemove0")

        testDir.resolve(id + "_0.jpg").toFile.exists === false

        browser.fill("#title").`with`("title02")
        browser.find("#siteDropDown option[value='1000']").click()
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents02');")
        browser.fill("#releaseDateTextBox").`with`("2016年02月02日")
        browser.find(".updateButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".message").getText === Messages("newsIsUpdated")
        browser.title === Messages("commonTitle", Messages("editNewsTitle"))
        browser.find(".newsTableBody .title").getText === "title02"
        browser.find(".newsTableBody .releaseTime").getText === "2016年02月02日"
        browser.find(".newsTableBody .site").getText === "商店111"
        browser.find(".newsTableBody .id a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("modifyNewsTitle"))
        browser.find("#title").getAttribute("value") === "title02"
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("return tinyMCE.activeEditor.getContent();") === "<p>Contents02</p>"
        browser.find("#releaseDateTextBox").getAttribute("value") === "2016年02月02日"
        browser.find("#siteDropDown option[selected='selected']").getText === "商店111"

        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code)
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".deleteButton").click()
        browser.await().atMost(10, TimeUnit.SECONDS).until(".no-button").isPresent
        browser.find(".no-button").click()
        browser.await().atMost(10, TimeUnit.SECONDS).until(".no-button").isPresent

        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code)
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".deleteButton").click()
        browser.await().atMost(10, TimeUnit.SECONDS).until(".yes-button").isPresent
        browser.find(".yes-button").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code)
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".deleteButton").size === 0
      }}
    }

    "Browse news" in {
      val app = FakeApplication(additionalConfiguration = inMemoryDatabase() ++ withTempDir)
      running(TestServer(3333, app), SeleniumHelpers.webDriver(Helpers.FIREFOX)) { browser => DB.withConnection { implicit conn =>
        implicit val lang = Lang("ja")
        val adminUser = loginWithTestUser(browser)
        val site1 = Site.createNew(LocaleInfo.Ja, "商店111")
        val site2 = Site.createNew(LocaleInfo.Ja, "商店222")
        val site3 = Site.createNew(LocaleInfo.Ja, "商店333")
        val site4 = Site.createNew(LocaleInfo.Ja, "商店444")
        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsMaintenance.startCreateNews().url.addParm("lang", lang.code)
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        // rec01
        browser.fill("#title").`with`("title01")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents01');")
        browser.fill("#releaseDateTextBox").`with`("2016年01月02日")
        browser.find("#siteDropDown option[value='1000']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").getText === Messages("newsIsCreated")

        // rec02
        browser.fill("#title").`with`("title02")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents02');")
        browser.fill("#releaseDateTextBox").`with`("2016年01月04日")
        browser.find("#siteDropDown option[value='1001']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").getText === Messages("newsIsCreated")

        // rec03
        browser.fill("#title").`with`("title03")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents03');")
        browser.fill("#releaseDateTextBox").`with`("2016年01月03日")
        browser.find("#siteDropDown option[value='1002']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").getText === Messages("newsIsCreated")

        // rec04(Future date)
        val futureDate: String = DateTimeFormat.forPattern("yyyy年MM月dd日").print(
          System.currentTimeMillis + 1000 * 60 * 60 * 24 * 2
        )

        browser.fill("#title").`with`("title04")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents03');")
        browser.fill("#releaseDateTextBox").`with`(futureDate)
        browser.find("#siteDropDown option[value='1003']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").getText === Messages("newsIsCreated")

        // In admin console, all news should be shown.
        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code)
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.title === Messages("commonTitle", Messages("editNewsTitle"))
        browser.find(".newsTableBody .title", 0).getText === "title04"
        browser.find(".newsTableBody .site", 0).getText === "商店444"
        browser.find(".newsTableBody .releaseTime", 0).getText === futureDate
        browser.find(".newsTableBody .title", 1).getText === "title02"
        browser.find(".newsTableBody .site", 1).getText === "商店222"
        browser.find(".newsTableBody .releaseTime", 1).getText === "2016年01月04日"
        browser.find(".newsTableBody .title", 2).getText === "title03"
        browser.find(".newsTableBody .site", 2).getText === "商店333"
        browser.find(".newsTableBody .releaseTime", 2).getText === "2016年01月03日"
        browser.find(".newsTableBody .title", 3).getText === "title01"
        browser.find(".newsTableBody .site", 3).getText === "商店111"
        browser.find(".newsTableBody .releaseTime", 3).getText === "2016年01月02日"

        // In normal console, future news should be hidden.
        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsQuery.list().url.addParm("lang", lang.code)
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTitle a", 0).getText === "title02"
        browser.find(".newsSite", 0).getText === "商店222"
        browser.find(".newsReleaseDate", 0).getText === "2016年01月04日"
        browser.find(".newsTitle a", 1).getText === "title03"
        browser.find(".newsSite", 1).getText === "商店333"
        browser.find(".newsReleaseDate", 1).getText === "2016年01月03日"
        browser.find(".newsTitle a", 2).getText === "title01"
        browser.find(".newsSite", 2).getText === "商店111"
        browser.find(".newsReleaseDate", 2).getText === "2016年01月02日"

        browser.find(".newsTitle a", 2).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val currentWindow = browser.webDriver.getWindowHandle
        val allWindows = browser.webDriver.getWindowHandles
        allWindows.remove(currentWindow)
        browser.webDriver.switchTo().window(allWindows.iterator.next)

        browser.title === Messages("commonTitle", Messages("news"))
        browser.find(".newsTitle").getText === "title01"
        browser.find(".newsReleaseDate").getText === "2016年01月02日"
        browser.find(".newsSite").getText === "商店111"
        browser.find(".newsContents").getText === "Contents01"

        // Paging
        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsQuery.pagedList().url.addParm("lang", lang.code)
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTable .body").size === 3

        browser.find(".newsTable .body .date", 0).getText === "2016年01月04日"
        browser.find(".newsTable .body .title", 0).getText === "title02"
        browser.find(".newsTable .body .site", 0).getText === "商店222"

        browser.find(".newsTable .body .date", 1).getText === "2016年01月03日"
        browser.find(".newsTable .body .title", 1).getText === "title03"
        browser.find(".newsTable .body .site", 1).getText === "商店333"

        browser.find(".newsTable .body .date", 2).getText === "2016年01月02日"
        browser.find(".newsTable .body .title", 2).getText === "title01"
        browser.find(".newsTable .body .site", 2).getText === "商店111"

        browser.goTo(
          "http://localhost:3333" + controllers.routes.NewsQuery.pagedList(page = 0, pageSize = 2).url.addParm("lang", lang.code)
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTable .body").size === 2

        browser.find(".newsTable .body .date", 0).getText === "2016年01月04日"
        browser.find(".newsTable .body .title", 0).getText === "title02"
        browser.find(".newsTable .body .site", 0).getText === "商店222"

        browser.find(".newsTable .body .date", 1).getText === "2016年01月03日"
        browser.find(".newsTable .body .title", 1).getText === "title03"
        browser.find(".newsTable .body .site", 1).getText === "商店333"

        browser.find(".nextPageButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTable .body").size === 1

        browser.find(".newsTable .body .date", 0).getText === "2016年01月02日"
        browser.find(".newsTable .body .title", 0).getText === "title01"
        browser.find(".newsTable .body .site", 0).getText === "商店111"

        browser.find(".prevPageButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTable .body").size === 2

        browser.find(".newsTable .body .date", 0).getText === "2016年01月04日"
        browser.find(".newsTable .body .title", 0).getText === "title02"
        browser.find(".newsTable .body .site", 0).getText === "商店222"

        browser.find(".newsTable .body .date", 1).getText === "2016年01月03日"
        browser.find(".newsTable .body .title", 1).getText === "title03"
        browser.find(".newsTable .body .site", 1).getText === "商店333"
      }}
    }
  }
}
