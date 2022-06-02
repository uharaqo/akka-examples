package shopping.cart.es

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityRef, EntityTypeKey }
import shopping.cart.es.ShoppingCart.Command
import shopping.cart.es.ShoppingCartCluster.EntityKey

object ShoppingCartCluster {

  val entityName: String = "ShoppingCart"

  private val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command](entityName)

  def newShardedEntity(): Entity[Command, ShardingEnvelope[Command]] =
    Entity(EntityKey)(ctx => ShoppingCartActor(ctx.entityId))
}

class ShoppingCartCluster(system: ActorSystem[_]) {
  private val sharding: ClusterSharding = ClusterSharding(system)

  def entityRefFor(cartId: String): EntityRef[Command] =
    sharding.entityRefFor(EntityKey, cartId)
}
