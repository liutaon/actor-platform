package im.actor.server

import akka.actor.ActorSystem
import slick.driver.PostgresDriver.api.Database

import im.actor.server.user.{ UserProcessor, UserProcessorRegion, UserViewRegion }

trait ImplicitUserRegions extends ImplicitSocialManagerRegion with ImplicitSeqUpdatesManagerRegion {
  protected implicit val system: ActorSystem
  protected implicit val db: Database

  protected implicit lazy val userProcessorRegion: UserProcessorRegion = UserProcessorRegion.start()
  protected implicit lazy val userViewRegion: UserViewRegion = UserViewRegion(userProcessorRegion.ref)
  UserProcessor.register()
}
