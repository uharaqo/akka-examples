package shopping.cart

object ShoppingCartTags {

  private val tags = Vector.tabulate(5)(i => s"carts-$i")

  val size: Int = tags.size

  def get(index: Int): String =
    tags(math.abs(index % tags.size))

  def forText(text: String): String =
    get(text.hashCode)
}
