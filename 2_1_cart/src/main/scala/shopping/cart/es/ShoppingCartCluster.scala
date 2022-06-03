package shopping.cart.es

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityRef, EntityTypeKey }
import shopping.cart.es.ShoppingCart.Command

trait ShoppingCartCluster {
  val entityName: String
  val size: Int

  def newShardedEntity()(implicit context: ShoppingCartContext): Entity[Command, ShardingEnvelope[Command]]

  def entityRefFor(cartId: String): EntityRef[Command]
}

object ShoppingCartCluster {

  def apply(entityName: String, clusterSize: Int)(implicit system: ActorSystem[_]): ShoppingCartCluster =
    new DefaultShoppingCartCluster(entityName, clusterSize, system)

  def disabled(): ShoppingCartCluster =
    new ShoppingCartCluster {
      override val entityName: String = "test"
      override val size: Int          = 1

      override def newShardedEntity()(
          implicit context: ShoppingCartContext
      ): Entity[Command, ShardingEnvelope[Command]] =
        throw new UnsupportedOperationException("Not Supported")

      override def entityRefFor(cartId: String): EntityRef[Command] =
        throw new UnsupportedOperationException("Not Supported")
    }
}

private class DefaultShoppingCartCluster(override val entityName: String, clusterSize: Int, system: ActorSystem[_])
    extends ShoppingCartCluster {

  private val sharding: ClusterSharding = ClusterSharding(system)
  private val EntityKey                 = EntityTypeKey[Command](entityName)

  override val size: Int = clusterSize

  override def newShardedEntity()(implicit context: ShoppingCartContext): Entity[Command, ShardingEnvelope[Command]] =
    Entity(EntityKey)(ctx => ShoppingCartActor(ctx.entityId))

  override def entityRefFor(cartId: String): EntityRef[Command] =
    sharding.entityRefFor(EntityKey, cartId)
}
