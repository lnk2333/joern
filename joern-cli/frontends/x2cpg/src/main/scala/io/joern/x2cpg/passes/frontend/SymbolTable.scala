package io.joern.x2cpg.passes.frontend

import io.shiftleft.codepropertygraph.generated.nodes._
import io.shiftleft.semanticcpg.language._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.MapView
import scala.collection.concurrent.TrieMap

/** Represents an identifier of some AST node at a specific scope.
  */
abstract class SBKey(val identifier: String) {

  /** Convenience methods to convert a node to a [[SBKey]].
    *
    * @param node
    *   the node to convert.
    * @return
    *   the corresponding [[SBKey]].
    */
  def fromNode(node: AstNode): SBKey

}

object SBKey {
  protected val logger: Logger = LoggerFactory.getLogger(getClass)
  def fromNodeToLocalKey(node: AstNode): LocalKey = {
    node match {
      case n: Identifier      => LocalVar(n.name)
      case n: Local           => LocalVar(n.name)
      case n: Call            => CallAlias(n.name)
      case n: Method          => CallAlias(n.name)
      case n: MethodRef       => CallAlias(n.code)
      case n: FieldIdentifier => LocalVar(n.canonicalName)
      case _ =>
        throw new RuntimeException(s"Local node of type ${node.label} is not supported in the type recovery pass.")
    }
  }

  def fromNodeToGlobalKey(node: AstNode): GlobalKey = node match {
    case n: FieldIdentifier => FieldVar(n.method.fullName, n.canonicalName)
    case n: Identifier      => FieldVar(n.method.fullName, n.name)
    case _ =>
      throw new RuntimeException(s"Global node of type ${node.label} is not supported in the type recovery pass.")
  }

}

/** Represents an identifier of some AST node at an intraprocedural scope.
  */
sealed class LocalKey(identifier: String) extends SBKey(identifier) {
  override def fromNode(node: AstNode): SBKey = SBKey.fromNodeToLocalKey(node)
}

/** A variable that holds data within an intraprocedural scope.
  */
case class LocalVar(override val identifier: String) extends LocalKey(identifier)

/** A name that refers to some kind of callee.
  */
case class CallAlias(override val identifier: String) extends LocalKey(identifier)

/** Represents an identifier of some AST node at an interprocedural scope.
  */
sealed class GlobalKey(identifier: String) extends SBKey(identifier) {
  override def fromNode(node: AstNode): SBKey = SBKey.fromNodeToGlobalKey(node)
}

/** Represents a field identifier at its declared computational unit.
  * @param compUnitFullName
  *   the computational unit's full name.
  * @param identifier
  *   the canonical name.
  */
case class FieldVar(compUnitFullName: String, override val identifier: String) extends GlobalKey(identifier)

/** A thread-safe symbol table that can represent multiple types per symbol. Each node in an AST gets converted to an
  * [[SBKey]] which gives contextual information to identify an AST entity. Each value in this table represents a set of
  * types that the key could be in a flow-insensitive manner.
  *
  * The [[SymbolTable]] operates like a map with a few convenient methods that are designed for this structure's
  * purpose.
  */
class SymbolTable[K <: SBKey](fromNode: AstNode => K) {

  private val table = TrieMap.empty[K, Set[String]]

  def apply(sbKey: K): Set[String] = table(sbKey)

  def apply(node: AstNode): Set[String] = table(fromNode(node))

  def from(sb: IterableOnce[(K, Set[String])]): SymbolTable[K] = {
    table.addAll(sb); this
  }

  def replaceWith(oldKey: K, newKey: K, newValues: Set[String]): Option[Set[String]] = {
    table.remove(oldKey)
    table.put(newKey, newValues)
  }

  def put(sbKey: K, typeFullNames: Set[String]): Option[Set[String]] =
    table.put(sbKey, typeFullNames)

  def put(sbKey: K, typeFullName: String): Option[Set[String]] =
    put(sbKey, Set(typeFullName))

  def put(node: AstNode, typeFullNames: Set[String]): Option[Set[String]] =
    put(fromNode(node), typeFullNames)

  def append(node: AstNode, typeFullName: String): Option[Set[String]] =
    append(node, Set(typeFullName))

  def append(node: AstNode, typeFullNames: Set[String]): Option[Set[String]] =
    append(fromNode(node), typeFullNames)

  def append(sbKey: K, typeFullNames: Set[String]): Option[Set[String]] = {
    table.get(sbKey) match {
      case Some(ts) => table.put(sbKey, ts ++ typeFullNames)
      case None     => table.put(sbKey, typeFullNames)
    }
  }

  def contains(sbKey: K): Boolean = table.contains(sbKey)

  def contains(node: AstNode): Boolean = contains(fromNode(node))

  def get(sbKey: K): Set[String] = table.getOrElse(sbKey, Set.empty)

  def get(node: AstNode): Set[String] = get(fromNode(node))

  def view: MapView[K, Set[String]] = table.view

  def clear(): Unit = table.clear()

}
