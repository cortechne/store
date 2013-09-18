package models

import anorm._
import anorm.{NotAssigned, Pk}
import anorm.SqlParser
import play.api.Play.current
import play.api.db._
import scala.language.postfixOps
import helpers.PasswordHash
import java.sql.Connection

case class StoreUser(
  id: Pk[Long] = NotAssigned,
  userName: String,
  firstName: String,
  middleName: Option[String],
  lastName: String,
  email: String,
  passwordHash: Long,
  salt: Long,
  deleted: Boolean,
  userRole: UserRole
) {
  def passwordMatch(password: String): Boolean =
    PasswordHash.generate(password, salt) == passwordHash
}

case class SiteUser(siteId: Long, storeUserId: Long, userRole: UserRole)

object StoreUser {
  val simple = {
    SqlParser.get[Pk[Long]]("store_user.store_user_id") ~
    SqlParser.get[String]("store_user.user_name") ~
    SqlParser.get[String]("store_user.first_name") ~
    SqlParser.get[Option[String]]("store_user.middle_name") ~
    SqlParser.get[String]("store_user.last_name") ~
    SqlParser.get[String]("store_user.email") ~
    SqlParser.get[Long]("store_user.password_hash") ~
    SqlParser.get[Long]("store_user.salt") ~
    SqlParser.get[Boolean]("store_user.deleted") ~
    SqlParser.get[Int]("store_user.user_role") map {
      case id~userName~firstName~middleName~lastName~email~passwordHash~salt~deleted~userRole => StoreUser(
        id, userName, firstName, middleName, lastName, email, passwordHash, salt, deleted, UserRole.byIndex(userRole)
      )
    }
  }

  def count(implicit conn: Connection) = 
    SQL("select count(*) from store_user").as(SqlParser.scalar[Long].single)

  def find(id: Long)(implicit conn: Connection): StoreUser =
    SQL(
      "select * from store_user where store_user_id = {id}"
    ).on(
      'id -> id
    ).as(StoreUser.simple.single)
  
  def findByUserName(userName: String)(implicit conn: Connection): Option[StoreUser] =
    SQL(
      "select * from store_user where user_name = {user_name}"
    ).on(
      'user_name -> userName
    ).as(StoreUser.simple.singleOpt)

  def all(implicit conn: Connection): Seq[StoreUser] =
    SQL(
      "select * from store_user"
    ).as(StoreUser.simple *)

  def create(
    userName: String, firstName: String, middleName: Option[String], lastName: String,
    email: String, passwordHash: Long, salt: Long, userRole: UserRole
  )(implicit conn: Connection): StoreUser = {
    SQL(
      """
      insert into store_user (
        store_user_id, user_name, first_name, middle_name, last_name, email, password_hash, salt, deleted, user_role
      ) values (
        (select nextval('store_user_seq')),
        {user_name}, {first_name}, {middle_name}, {last_name}, {email}, {password_hash}, {salt}, false, {user_role}
      )
      """
    ).on(
      'user_name -> userName,
      'first_name -> firstName,
      'middle_name -> middleName,
      'last_name -> lastName,
      'email -> email,
      'password_hash -> passwordHash,
      'salt -> salt,
      'user_role -> userRole.ordinal
    ).executeUpdate()

    val storeUserId = SQL("select currval('store_user_seq')").as(SqlParser.scalar[Long].single)
    StoreUser(Id(storeUserId), userName, firstName, middleName, lastName, email, passwordHash, salt,  false, userRole)
  }
}

object SiteUser {
  val simple = {
    SqlParser.get[Long]("site_user.site_id") ~
    SqlParser.get[Long]("site_user.store_user_id") ~
    SqlParser.get[Int]("site_user.user_role") map {
      case siteId~storeUserId~userRole => SiteUser(siteId, storeUserId, UserRole.byIndex(userRole))
    }
  }
}