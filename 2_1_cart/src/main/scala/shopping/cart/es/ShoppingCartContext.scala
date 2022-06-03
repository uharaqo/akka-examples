package shopping.cart.es

import akka.actor.typed.ActorSystem

class ShoppingCartContext(val system: ActorSystem[_], val cluster: ShoppingCartCluster) {

  private val tags = Vector.tabulate(cluster.size)(i => s"carts-$i")

  def getTag(index: Int): String =
    tags(math.abs(index % tags.size))

  def getTag(text: String): String =
    getTag(text.hashCode)
}
