package io.joern.ghidra2cpg.fixtures

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes._
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.joern.x2cpg.X2Cpg.applyDefaultOverlays
import io.shiftleft.semanticcpg.language._
import io.shiftleft.semanticcpg.language.dotextension.ImageViewer
import io.shiftleft.semanticcpg.layers._
import overflowdb.traversal.Traversal

import scala.sys.process.Process
import scala.util.Try

class DataFlowBinToCpgSuite extends GhidraBinToCpgSuite {

  implicit var context: EngineContext = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    context = EngineContext()
  }

  implicit val viewer: ImageViewer = (pathStr: String) =>
    Try {
      Process(Seq("xdg-open", pathStr)).!!
    }

  override def passes(cpg: Cpg): Unit = {
    applyDefaultOverlays(cpg)
    val context = new LayerCreatorContext(cpg)
    val options = new OssDataFlowOptions()
    new OssDataFlow(options).run(context)
  }

  protected implicit def int2IntegerOption(x: Int): Option[Integer] =
    Some(x)

  protected def getMemberOfType(cpg: Cpg, typeName: String, memberName: String): Traversal[Member] =
    cpg.typeDecl.nameExact(typeName).member.nameExact(memberName)

  protected def getMethodOfType(cpg: Cpg, typeName: String, methodName: String): Traversal[Method] =
    cpg.typeDecl.nameExact(typeName).method.nameExact(methodName)

  protected def getLiteralOfType(cpg: Cpg, typeName: String, literalName: String): Traversal[Literal] =
    cpg.typeDecl.nameExact(typeName).method.isLiteral.codeExact(literalName)
}
