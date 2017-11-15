package models

import org.joda.time.DateTime
import play.api.db.DB
import play.api.Play.current
import java.sql.Connection

case class ChangeSiteItemMetadataTable(
  siteItemMetadata: Seq[ChangeSiteItemMetadata]
) {
  def update(itemId: Long)(implicit conn: Connection) {
    siteItemMetadata.foreach {
      _.update(itemId)
    }
  }
}

case class ChangeSiteItemMetadata(
  id: Long, siteId: Long, metadataType: Int, metadata: Long,
  var validUntil: DateTime = new DateTime(9999, 12, 31, 23, 59, 59)
) {
  def update(itemId: Long)(implicit conn: Connection) {
    SiteItemNumericMetadata.update(id, metadata, validUntil.getMillis)
  }
}
