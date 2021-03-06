package controllers

import play.api.i18n.Lang
import play.api._
import db.DB
import mvc.Controller
import mvc.Result
import play.twirl.api.Html
import mvc.RequestHeader
import play.api.Play.current
import controllers.I18n.I18nAware
import models._
import collection.immutable
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import java.sql.Connection

object OrderHistory extends Controller with NeedLogin with HasLogger with I18nAware {
  val orderHistoryForm = Form(
    mapping(
      "year" -> number(min = YearMonth.MinYear, max = YearMonth.MaxYear),
      "month" -> number(min = 1, max = 12),
      "command" -> text
    )(YearMonth.apply)(YearMonth.unapply)
  )

  def index() = NeedAuthenticated { implicit request =>
    implicit val login = request.user
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      Ok(views.html.orderHistory(orderHistoryForm))
  }

  type ShowOrderView = (
    PagedRecords[TransactionSummaryEntry],
    immutable.LongMap[Seq[TransactionDetail]],
    immutable.LongMap[scala.collection.immutable.LongMap[TransactionLogShipping]],
    immutable.LongMap[PersistedTransaction],
    immutable.Map[Long, Address],
    Option[Long]
  ) => Html

  def showOrderHistoryList(
    page: Int, pageSize: Int, orderBySpec: String, tranId: Option[Long]
  ) = NeedAuthenticated { implicit request =>
    implicit val login = request.user
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      DB.withConnection { implicit conn =>
        showOrderHistoryInternal(
          page: Int, pageSize: Int, orderBySpec: String, tranId,
          views.html.showOrderHistoryList.apply
        )
      }
  }

  def showOrderHistory(
    page: Int, pageSize: Int, orderBySpec: String, tranId: Option[Long]
  ) = NeedAuthenticated { implicit request =>
    implicit val login = request.user
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      DB.withConnection { implicit conn =>
        val pagedRecords: PagedRecords[TransactionSummaryEntry] =
          TransactionSummary.list(
            storeUserId = Some(login.storeUser.id.get), tranId = tranId,
            page = page, pageSize = pageSize, orderBy = OrderBy(orderBySpec)
          )
        val siteTranByTranId: immutable.LongMap[PersistedTransaction] =
          TransactionSummary.getSiteTranByTranId(pagedRecords.records, request2lang)

        showOrderHistoryInternal(
          page, pageSize, orderBySpec, tranId,
          views.html.showOrderHistory.apply
        )
      }
  }

  def showOrderHistoryInternal(
    page: Int, pageSize: Int, orderBySpec: String,
    tranId: Option[Long],
    view: ShowOrderView
  )(
    implicit login: LoginSession,
    request: RequestHeader,
    conn: Connection
  ): Result = {
    val pagedRecords = TransactionSummary.list(
      storeUserId = Some(login.storeUser.id.get), tranId = tranId,
      page = page, pageSize = pageSize, orderBy = OrderBy(orderBySpec)
    )
    val siteTranByTranId: immutable.LongMap[PersistedTransaction] =
      TransactionSummary.getSiteTranByTranId(pagedRecords.records, request2lang)

    Ok(
      view(
        pagedRecords,
        TransactionSummary.getDetailByTranSiteId(pagedRecords.records),
        AccountingBill.getBoxBySiteAndItemSize(pagedRecords.records),
        siteTranByTranId, 
        AccountingBill.getAddressTable(siteTranByTranId),
        tranId
      )
    )
  }

  def showMonthly() = NeedAuthenticated { implicit request =>
    implicit val login = request.user
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      orderHistoryForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(
            views.html.orderHistory(formWithErrors)
          )
        },
        yearMonth => {
          DB.withConnection { implicit conn =>
            val summaries = TransactionSummary.listByPeriod(
              storeUserId = Some(login.storeUser.id.get), yearMonth = yearMonth
            )
            val siteTranByTranId = TransactionSummary.getSiteTranByTranId(summaries, request2lang)
            Ok(views.html.showMonthlyOrderHistory(
              orderHistoryForm.fill(yearMonth),
              summaries,
              TransactionSummary.getDetailByTranSiteId(summaries),
              AccountingBill.getBoxBySiteAndItemSize(summaries),
              siteTranByTranId,
              AccountingBill.getAddressTable(siteTranByTranId)
            ))
          }
        }
      )
  }
}
