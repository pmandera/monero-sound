import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.gzip.GzipFilter

/** Play Farmework filter for serving compressed content */
class Filters @Inject() (gzipFilter: GzipFilter)
  extends DefaultHttpFilters(gzipFilter)
