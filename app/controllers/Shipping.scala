package controllers

import play.api._
import data.Form
import db.DB
import play.api.mvc._
import models._
import play.api.Play.current

import java.util.Locale
import java.util.regex.Pattern
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import helpers.Enums
import controllers.I18n.I18nAware
import java.sql.Connection

object Shipping extends Controller with NeedLogin with HasLogger with I18nAware {
  val Zip1Pattern = Pattern.compile("\\d{3}")
  val Zip2Pattern = Pattern.compile("\\d{4}")
  val TelPattern = Pattern.compile("\\d+{1,32}")
  val TelOptionPattern = Pattern.compile("\\d{0,32}")

  val jaForm = Form(
    mapping(
      "firstName" -> text.verifying(nonEmpty, maxLength(64)),
      "lastName" -> text.verifying(nonEmpty, maxLength(64)),
      "firstNameKana" -> text.verifying(nonEmpty, maxLength(64)),
      "lastNameKana" -> text.verifying(nonEmpty, maxLength(64)),
      "zip1" -> text.verifying(z => Zip1Pattern.matcher(z).matches),
      "zip2" -> text.verifying(z => Zip2Pattern.matcher(z).matches),
      "prefecture" -> number,
      "address1" -> text.verifying(nonEmpty, maxLength(256)),
      "address2" -> text.verifying(nonEmpty, maxLength(256)),
      "address3" -> text.verifying(maxLength(256)),
      "address4" -> text.verifying(maxLength(256)),
      "address5" -> text.verifying(maxLength(256)),
      "tel1" -> text.verifying(Messages("error.number"), z => TelPattern.matcher(z).matches),
      "tel2" -> text.verifying(Messages("error.number"), z => TelOptionPattern.matcher(z).matches),
      "tel3" -> text.verifying(Messages("error.number"), z => TelOptionPattern.matcher(z).matches)
    )(CreateAddress.apply4Japan)(CreateAddress.unapply4Japan)
  )

  def startEnterShippingAddress = isAuthenticated { login => implicit request =>
    DB.withConnection { implicit conn =>
      val addr: Option[Address] = ShippingAddressHistory.list(login.userId).headOption.map {
        h => Address.byId(h.addressId)
      }
      val form = addr match {
        case Some(a) =>
          jaForm.fill(CreateAddress.fromAddress(a))
        case None => jaForm
      }
      lang.toLocale match {
        case Locale.JAPANESE =>
          Ok(views.html.shippingAddressJa(form, Address.JapanPrefectures))
        case Locale.JAPAN =>
          Ok(views.html.shippingAddressJa(form, Address.JapanPrefectures))
        
        case _ =>
          Ok(views.html.shippingAddressJa(form, Address.JapanPrefectures))
      }
    }
  }

  def enterShippingAddressJa = isAuthenticated { login => implicit request =>
    jaForm.bindFromRequest.fold(
      formWithErrors => {
        logger.error("Validation error in Shipping.enterShippingAddress.")
        DB.withConnection { implicit conn =>
          BadRequest(views.html.shippingAddressJa(formWithErrors, Address.JapanPrefectures))
        }
      },
      newShippingAddress => {
        DB.withTransaction { implicit conn => {
          newShippingAddress.save(login.userId)
          Redirect(routes.Shipping.confirmShippingAddressJa())
        }}
      }
    )
  }

  def confirmShippingAddressJa = isAuthenticated { login => implicit request =>
    DB.withConnection { implicit conn =>
      val cart = ShoppingCartItem.listItemsForUser(LocaleInfo.getDefault, login.userId)
      val his = ShippingAddressHistory.list(login.userId).head
      val addr = Address.byId(his.addressId)
      try {
        Ok(views.html.confirmShippingAddressJa(cart, addr, shippingFee(addr, cart)))
      }
      catch {
        case e: CannotShippingException => {
          Ok(views.html.cannotShipJa(cannotShip(cart, e, addr), addr, e.siteId, e.itemClass))
        }
      }
    }
  }


  def cannotShip(
    cart: ShoppingCartTotal, e: CannotShippingException, addr: Address
  ): Seq[ShoppingCartTotalEntry] = {
    cart.table.filter { item =>
      item.siteItemNumericMetadata.get(SiteItemNumericMetadataType.SHIPPING_SIZE) match {
        case None => true
        case Some(itemClass) =>
          e.isCannotShip(
            item.site.id.get,
            addr.prefecture.code,
            itemClass.metadata
          )
      }
    }
  }

  def shippingFee(
    addr: Address, cart: ShoppingCartTotal
  )(implicit conn: Connection): ShippingTotal = {
    ShippingFeeHistory.feeBySiteAndItemClass(
      CountryCode.JPN, addr.prefecture.code,
      cart.table.foldLeft(ShippingFeeEntries()) {
        (sum, e) =>
          sum.add(
            e.shoppingCartItem.siteId,
            e.siteItemNumericMetadata.get(SiteItemNumericMetadataType.SHIPPING_SIZE).map(_.metadata).getOrElse(1L),
            e.shoppingCartItem.quantity
          )
      }
    )
  }

  def finalizeTransaction = isAuthenticated { login => implicit request =>
    DB.withTransaction { implicit conn =>
      val cart = ShoppingCartItem.listItemsForUser(LocaleInfo.getDefault, login.userId)
      val his = ShippingAddressHistory.list(login.userId).head
      val addr = Address.byId(his.addressId)
      try {
        Transaction.save(cart, addr, shippingFee(addr, cart))
        Ok("save")
      }
      catch {
        case e: CannotShippingException => {
          Ok(views.html.cannotShipJa(cannotShip(cart, e, addr), addr, e.siteId, e.itemClass))
        }
      }
    }
  }
}