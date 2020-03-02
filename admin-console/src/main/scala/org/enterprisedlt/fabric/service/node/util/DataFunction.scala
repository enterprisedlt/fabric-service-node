package org.enterprisedlt.fabric.service.node.util

import monocle.Lens

/**
 * @author Alexey Polubelov
 */
object DataFunction {

    trait GetFunction[T, V] {
        def get(t: T): V
    }

    trait SetFunction[T, V] {
        def set(v: V): T => T
    }

    trait ModifyFunction[T, V] {
        def modify(mf: V => V): T => T
    }

    trait GetSetModifyFunctions[T, V] extends GetFunction[T, V] with SetFunction[T, V] with ModifyFunction[T, V]

    class LensWrapper[X, Y](l: Lens[X, Y]) extends GetSetModifyFunctions[X, Y] {
        override def set(v: Y): X => X = l.set(v)

        override def get(t: X): Y = l.get(t)

        override def modify(mf: Y => Y): X => X = l.modify(mf)
    }

    class GSMFunctionsComposition[X, Y, Z](f1: GetSetModifyFunctions[X, Y], f2: GetSetModifyFunctions[Y, Z]) extends GetSetModifyFunctions[X, Z] {
        override def get(t: X): Z = f2.get(f1.get(t))

        override def set(v: Z): X => X = f1.modify(f2.set(v))

        override def modify(mf: Z => Z): X => X = f1.modify(f2.modify(mf))
    }

    implicit class ComposeGSMFunctions[A, B](self: GetSetModifyFunctions[A, B]) {
        def /[D](other: GetSetModifyFunctions[B, D]): GetSetModifyFunctions[A, D] = new GSMFunctionsComposition[A, B, D](self, other)

        def /[D](other: Lens[B, D]): GetSetModifyFunctions[A, D] = self / new LensWrapper(other)
    }

    implicit class ComposeLens[A, B](self: Lens[A, B]) {

        def /[D](other: GetSetModifyFunctions[B, D]): GetSetModifyFunctions[A, D] = new LensWrapper(self) / other

        def /[D](other: Lens[B, D]): GetSetModifyFunctions[A, D] = new LensWrapper(self) / new LensWrapper(other)
    }


    implicit class HGSFLinks[B, Y](dst: GetSetModifyFunctions[B, Y]) {
        // lens + lens
        def <~~[A, X](src: GetSetModifyFunctions[A, X])(mapping: X => Y): (B, A) => B = (s, g) => dst.set(mapping(src.get(g)))(s)

        // lens + (lens & condition)
        def <~~[A, X](source: HGSFWithCondition[A, X])(mapping: X => Y): (B, A) => B = { (s, g) =>
            val v = source.lens.get(g)
            if (source.condition(v)) dst.set(mapping(v))(s) else s
        }

        def when(condition: Y => Boolean): HGSFWithCondition[B, Y] = HGSFWithCondition(dst, condition)

    }

    implicit class HGSFWithConditionLinks[B, Y](dest: HGSFWithCondition[B, Y]) {
        // (lens & condition) + lens
        def <~~[A, X](src: GetSetModifyFunctions[A, X])(mapping: X => Y): (B, A) => B = { (s, g) =>
            val x = src.get(g)
            val y = dest.lens.get(s)
            if (dest.condition(y)) dest.lens.set(mapping(x))(s) else s
        }

        // (lens & condition) + lens
        def <~~[A, X](source: HGSFWithCondition[A, X])(mapping: X => Y): (B, A) => B = { (s, g) =>
            val x = source.lens.get(g)
            val y = dest.lens.get(s)
            if (source.condition(x) && dest.condition(y)) dest.lens.set(mapping(x))(s) else s
        }

    }

    implicit class LensesLinks[B, Y](dst: Lens[B, Y]) {
        // lens + lens
        def <~~[A, X](src: GetSetModifyFunctions[A, X])(mapping: X => Y): (B, A) => B = (s, g) => dst.set(mapping(src.get(g)))(s)

        def <~~[A, X](src: Lens[A, X])(mapping: X => Y): (B, A) => B = (s, g) => dst.set(mapping(src.get(g)))(s)

        // lens + (lens & condition)
        def <~~[A, X](source: HGSFWithCondition[A, X])(mapping: X => Y): (B, A) => B = { (s, g) =>
            val v = source.lens.get(g)
            if (source.condition(v)) dst.set(mapping(v))(s) else s
        }

        def when(condition: Y => Boolean): HGSFWithCondition[B, Y] = HGSFWithCondition(new LensWrapper(dst), condition)

    }

    case class HGSFWithCondition[A, X](
        lens: GetSetModifyFunctions[A, X],
        condition: X => Boolean
    )

}

