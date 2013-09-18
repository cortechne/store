package models

import java.sql.SQLException
;

object ExceptionMapper {
  def mapException[T](block: => T): T = try {
    block
  }
  catch {
    case e: SQLException => {
      val className = e.getClass.getName

      className match {
        case "org.postgresql.util.PSQLException" => e.getSQLState() match {
          case "23505" => throw new UniqueConstraintException(e)
        }
      }
    }
  }
}