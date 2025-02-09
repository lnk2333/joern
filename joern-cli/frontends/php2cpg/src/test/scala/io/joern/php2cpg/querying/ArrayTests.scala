package io.joern.php2cpg.querying

import io.joern.php2cpg.parser.Domain.PhpOperators
import io.joern.php2cpg.testfixtures.PhpCode2CpgFixture
import io.shiftleft.codepropertygraph.generated.Operators
import io.shiftleft.codepropertygraph.generated.nodes.{Block, Call, Identifier, Literal, Local}
import io.shiftleft.semanticcpg.language._

class ArrayTests extends PhpCode2CpgFixture {
  "array accesses with variable keys should be represented as index accesses" in {
    val cpg = code("<?php\n$array[$key]")

    inside(cpg.call.l) { case List(indexAccess) =>
      indexAccess.name shouldBe Operators.indexAccess
      indexAccess.code shouldBe "$array[$key]"
      indexAccess.lineNumber shouldBe Some(2)

      inside(indexAccess.argument.l) { case List(array: Identifier, key: Identifier) =>
        array.name shouldBe "array"
        array.code shouldBe "$array"
        array.lineNumber shouldBe Some(2)

        key.name shouldBe "key"
        key.code shouldBe "$key"
        key.lineNumber shouldBe Some(2)
      }
    }
  }

  "array accesses with literal keys should be represented as index accesses" in {
    val cpg = code("<?php\n$array[0]")

    inside(cpg.call.l) { case List(indexAccess) =>
      indexAccess.name shouldBe Operators.indexAccess
      indexAccess.code shouldBe "$array[0]"
      indexAccess.lineNumber shouldBe Some(2)

      inside(indexAccess.argument.l) { case List(array: Identifier, key: Literal) =>
        array.name shouldBe "array"
        array.code shouldBe "$array"
        array.lineNumber shouldBe Some(2)

        key.code shouldBe "0"
        key.lineNumber shouldBe Some(2)
      }
    }
  }

  "array accesses without keys should be represented as emptyArrayIdx calls" in {
    val cpg = code("<?php\n$array[]")

    inside(cpg.call.l) { case List(access) =>
      access.name shouldBe PhpOperators.emptyArrayIdx
      access.code shouldBe "$array[]"
      access.lineNumber shouldBe Some(2)

      inside(access.argument.l) { case List(array: Identifier) =>
        array.name shouldBe "array"
        array.code shouldBe "$array"
        array.lineNumber shouldBe Some(2)
      }
    }
  }

  "associative array definitions should be lowered with the correct assignments" in {
    val cpg = code("""<?php
        |array(
        |  "A" => 1,
        |  "B" => 2
        |)
        |""".stripMargin)

    inside(cpg.method.internal.body.astChildren.collectAll[Block].l) { case List(arrayBlock) =>
      inside(arrayBlock.astChildren.l) {
        case List(tmpLocal: Local, aAssign: Call, bAssign: Call, tmpIdent: Identifier) =>
          tmpLocal.name shouldBe "tmp0"
          tmpLocal.code shouldBe "$tmp0"

          aAssign.code shouldBe "$tmp0[\"A\"] = 1"
          aAssign.lineNumber shouldBe Some(3)

          bAssign.code shouldBe "$tmp0[\"B\"] = 2"
          bAssign.lineNumber shouldBe Some(4)

          tmpIdent.name shouldBe "tmp0"
          tmpIdent.code shouldBe "$tmp0"
          tmpIdent._localViaRefOut should contain(tmpLocal)
      }
    }
  }

  "non-associative array definitions should be lowered with the correct index accesses and assignments" in {
    val cpg = code("""<?php
        |array(
        |  "A",
        |  "B"
        |)
        |""".stripMargin)

    inside(cpg.method.internal.body.astChildren.collectAll[Block].l) { case List(arrayBlock) =>
      inside(arrayBlock.astChildren.l) {
        case List(tmpLocal: Local, aAssign: Call, bAssign: Call, tmpIdent: Identifier) =>
          tmpLocal.name shouldBe "tmp0"
          tmpLocal.code shouldBe "$tmp0"

          aAssign.code shouldBe "$tmp0[0] = \"A\""
          aAssign.lineNumber shouldBe Some(3)

          bAssign.code shouldBe "$tmp0[1] = \"B\""
          bAssign.lineNumber shouldBe Some(4)

          tmpIdent.name shouldBe "tmp0"
          tmpIdent.code shouldBe "$tmp0"
          tmpIdent._localViaRefOut should contain(tmpLocal)
      }
    }
  }

  "arrays with int-compatible indices should have them treated as ints" in {
    val cpg = code("""<?php
        |array(
        |  "2" => "A"
        |)
        |""".stripMargin)

    inside(cpg.method.internal.body.astChildren.collectAll[Block].l) { case List(arrayBlock) =>
      inside(arrayBlock.astChildren.l) { case List(tmpLocal: Local, assign: Call, tmpIdent: Identifier) =>
        tmpLocal.name shouldBe "tmp0"
        tmpLocal.code shouldBe "$tmp0"

        assign.code shouldBe "$tmp0[2] = \"A\""
        inside(assign.argument.collectAll[Call].argument.l) { case List(array: Identifier, index: Literal) =>
          array.name shouldBe "tmp0"
          array.code shouldBe "$tmp0"

          index.code shouldBe "2"
          index.typeFullName shouldBe "int"
        }

        tmpIdent.name shouldBe "tmp0"
        tmpIdent.code shouldBe "$tmp0"
        tmpIdent._localViaRefOut should contain(tmpLocal)
      }
    }
  }

  "mixed associative array definitions should be represented with correct keys" in {
    val cpg = code("""<?php
        |array(
        |  "A" => "B",
        |  "C",
        |  4 => "D",
        |  "E",
        |  "10" => "F",
        |  "G",
        |  8 => "H",
        |)
        |""".stripMargin)

    inside(cpg.method.internal.body.astChildren.collectAll[Block].l) { case List(arrayBlock) =>
      inside(arrayBlock.astChildren.l) {
        case List(
              tmpLocal: Local,
              aAssign: Call,
              cAssign: Call,
              fourAssign: Call,
              eAssign: Call,
              tenAssign: Call,
              gAssign: Call,
              eightAssign: Call,
              tmpIdent: Identifier
            ) =>
          tmpLocal.name shouldBe "tmp0"
          tmpLocal.code shouldBe "$tmp0"

          aAssign.code shouldBe "$tmp0[\"A\"] = \"B\""
          cAssign.code shouldBe "$tmp0[0] = \"C\""
          fourAssign.code shouldBe "$tmp0[4] = \"D\""
          eAssign.code shouldBe "$tmp0[5] = \"E\""
          tenAssign.code shouldBe "$tmp0[10] = \"F\""
          gAssign.code shouldBe "$tmp0[11] = \"G\""
          eightAssign.code shouldBe "$tmp0[8] = \"H\""

          tmpIdent.name shouldBe "tmp0"
          tmpIdent.code shouldBe "$tmp0"
          tmpIdent._localViaRefOut should contain(tmpLocal)
      }
    }
  }
}
