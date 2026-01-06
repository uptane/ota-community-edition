package com.advancedtelematic.tuf.cli

import com.advancedtelematic.tuf.cli.Commands.Command
import shapeless._
import scopt.OptionDef
import shapeless.ops.record.Modifier

object GenericOptionDefOps {

  implicit class OptionDefGenericActionBuild[A](value: OptionDef[A, Config]) {
    val CGen = LabelledGeneric[Config]

    def toConfigParam(witness: Witness)(
      implicit mod: Modifier.Aux[CGen.Repr, witness.T, A, A, CGen.Repr]): OptionDef[A, Config] = {
      val updateAction = (arg: A, c: Config) => {
        val rec = mod.apply(CGen.to(c), _ => arg)
        CGen.from(rec)
      }

      value.action(updateAction)
    }

    def toConfigOptionParam(witness: Witness)(
      implicit mod: Modifier.Aux[CGen.Repr, witness.T, Option[A], Option[A], CGen.Repr])
      : OptionDef[A, Config] = {
      val updateAction = (arg: A, c: Config) => {
        val rec = mod.apply(CGen.to(c), _ => Option(arg))
        CGen.from(rec)
      }

      value.action(updateAction)
    }

  }

  implicit class OptionDefConfigCommandAction(value: OptionDef[Unit, Config]) {

    def toCommand(command: Command): OptionDef[Unit, Config] =
      value.action((_, c) => c.copy(command = command))

  }

}
