/**
 * What Customers also bought with a Product: the other Products in the Orders that bought it, paid
 * and kept, most bought together first. Each is a Recommendation, with how many Orders bought both.
 */
export interface AlsoBought {
  sku: string
  items: { sku: string; orders: number }[]
}
