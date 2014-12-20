package functional

import java.util.concurrent.TimeUnit
import play.api.test.Helpers._
import play.api.Play.current
import helpers.Helper._
import models._
import org.joda.time.format.DateTimeFormat
import play.api.db.DB
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer, FakeApplication}
import anorm._
import play.api.i18n.{Lang, Messages}
import java.sql.Date.{valueOf => date}
import LocaleInfo._
import java.sql.Connection
import scala.collection.JavaConversions._
import com.ruimo.scoins.Scoping._
import controllers.NeedLogin.passwordMinLength

class RegisterUserInformationSpec extends Specification {
  "User information registration" should {
    "Show error message for blank input" in {
      val app = FakeApplication(additionalConfiguration = inMemoryDatabase())
      running(TestServer(3333, app), Helpers.FIREFOX) { browser => DB.withConnection { implicit conn =>
        implicit val lang = Lang("ja")
        // Create tentative user.
        SQL("""
          insert into store_user (
            store_user_id, user_name, first_name, middle_name, last_name,
            email, password_hash, salt, deleted, user_role, company_name
          ) values (
            1, '002-Uno', '', null, '',
            '', 6442108903620542185, -3926372532362629068,
            FALSE, """ + UserRole.NORMAL.ordinal + """, null
          )""").executeUpdate()

        // Since we are not loged-in. The following page request should navigate to login page.
        browser.goTo(
          "http://localhost:3333" + controllers.routes.UserEntry.registerUserInformation() + "?lang=" + lang.code
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.title === Messages("loginTitle")
        browser.fill("#userName").`with`("002-Uno")
        browser.fill("#password").`with`("Uno")
        browser.find("#doLoginButton").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.title === Messages("registerUserInformation")
        
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").getText === Messages("inputError")
        browser.find("#password_main_field .help-inline").getText === Messages("error.minLength", passwordMinLength)
        browser.find("#firstName_field .help-inline").getText === Messages("error.required")
        browser.find("#lastName_field .help-inline").getText === Messages("error.required")
        browser.find("#firstNameKana_field .help-inline").getText === Messages("error.required")
        browser.find("#lastNameKana_field .help-inline").getText === Messages("error.required")
        browser.find("#email_field .help-inline").getText === Messages("error.email")
        browser.find("#zip_field .help-inline").getText === Messages("zipError")
        browser.find("#address1_field .help-inline").getText === Messages("error.required")
        browser.find("#address2_field .help-inline").getText === Messages("error.required")
        browser.find("#address3_field .help-inline").getText === ""
        browser.find("#tel1_field .help-inline").getText === Messages("error.number")

        val password = "1" * (passwordMinLength - 1)
        browser.fill("#password_main").`with`(password)
        browser.fill("#password_confirm").`with`(password)
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_main_field .help-inline").getText === Messages("error.minLength", passwordMinLength)

        browser.fill("#password_main").`with`("12345678")
        browser.fill("#password_confirm").`with`("12345679")
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_confirm_field .help-inline").getText === Messages("confirmPasswordDoesNotMatch")

        // current password does not match
        browser.fill("#currentPassword").`with`("foobar")
        browser.fill("#password_main").`with`("12345678")
        browser.fill("#password_confirm").`with`("12345678")
        browser.fill("#firstName").`with`("first name")
        browser.fill("#lastName").`with`("last name")
        browser.fill("#firstNameKana").`with`("first name kana")
        browser.fill("#lastNameKana").`with`("last name kana")
        browser.fill("#email").`with`("null@ruimo.com")
        browser.fill("#zip_field input[name='zip1']").`with`("146")
        browser.fill("#zip_field input[name='zip2']").`with`("0082")
        browser.fill("#address1").`with`("address 1")
        browser.fill("#address2").`with`("address 2")
        browser.fill("#tel1").`with`("11111111")

        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#currentPassword_field .help-inline").getText === Messages("currentPasswordNotMatch")

        // new password(confirm) does not match
        browser.fill("#currentPassword").`with`("Uno")
        browser.fill("#password_main").`with`("12345678")
        browser.fill("#password_confirm").`with`("12345679")

        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_confirm_field .help-inline").getText === Messages("confirmPasswordDoesNotMatch")

        // new password is too easy.
        // Add naive password to dictionary.
        SQL("""
          insert into password_dict (password) values ('password')
        """).executeUpdate()
        browser.fill("#currentPassword").`with`("Uno")
        browser.fill("#password_main").`with`("password")
        browser.fill("#password_confirm").`with`("password")
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_main_field .help-inline").getText === Messages("naivePassword")
        
        // User information should be registered.

        browser.fill("#currentPassword").`with`("Uno")
        browser.fill("#password_main").`with`("passwor0")
        browser.fill("#password_confirm").`with`("passwor0")
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(StoreUser.findByUserName("002-Uno").get) { u =>
          u.userName === "002-Uno"
          u.firstName === "first name"
          u.middleName === None
          u.lastName === "last name"
          u.email === "null@ruimo.com"
          u.passwordHash === 6442108903620542185L
          u.salt === -3926372532362629068L
          u.deleted === false
          u.userRole === UserRole.NORMAL
          u.companyName === None
        }
      }}
    }
  }
}
