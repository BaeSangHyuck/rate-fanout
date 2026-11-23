rootProject.name = "rate-fanout"

include(":libs:common")

include(":domain:quote:quote-domain")
include(":domain:quote:quote-application")

include(":domain:order:order-domain")
include(":domain:order:order-application")

include(":domain:coupon:coupon-domain")
include(":domain:coupon:coupon-application")

include(":monolith:fanout-app")
