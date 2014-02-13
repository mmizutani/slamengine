package slamdata.engine.analysis

import Function.untupled

import scalaz._

import scalaz.std.list._
import scalaz.std.vector._
import scalaz.std.map._
import scalaz.std.tuple._
import scalaz.std.iterable._

object Analysis {
  def readTree[N, A, B, E](f: AnnotatedTree[N, A] => Analysis[N, A, B, E]): Analysis[N, A, B, E] = tree => {
    f(tree)(tree)
  }

  def annotate[N, A, B, E: Semigroup](f: N => Validation[E, B]): Analysis[N, A, B, E] = tree => {
    Traverse[List].sequence[({type f[a]=Validation[E, a]})#f, (N, B)](tree.nodes.map(n => f(n).map(b => (n, b)))).map { list =>
      tree.annotate(list.toMap)
    }
  }

  def fork[N, A, B, E: Semigroup](analyzer: Analyzer[N, B, E]): Analysis[N, A, B, E] = tree => {
    implicit val sg = Semigroup.firstSemigroup[B]    

    tree.fork(Map.empty[N, B])({ (acc, node) =>
      analyzer(acc.apply, node).map(b => acc + (node -> b))
    }).map { vector =>
      tree.annotate(Traverse[Vector].foldMap(vector)(identity))
    }
  }

  def join[N, A, B, E: Semigroup](analyzer: Analyzer[N, B, E]): Analysis[N, A, B, E] = tree => {
    implicit val sg = Semigroup.firstSemigroup[B]

    (tree.join(Map.empty[N, B])((acc: Map[N, B], node: N) => {
      analyzer(acc.apply, node).map { b =>
        acc + (node -> b)
      }
    })).map(tree.annotate _)
  }

  def loop[N, A, E: Semigroup](condition: AnnotatedTree[N, A] => Boolean)
    (push: Analyzer[N, A, E], pull: Analyzer[N, A, E]): Analysis[N, A, A, E] = {
    def loopWhile0(tree: AnnotatedTree[N, A]): Validation[E, AnnotatedTree[N, A]] = {
      if (!condition(tree)) Validation.success(tree)
      else {
        fork[N, A, A, E](push)(Semigroup[E])(tree).fold(
          Validation.failure,
          tree2 => {
            join[N, A, A, E](pull)(Semigroup[E])(tree2)
          }
        )
      }
    }

    (tree: AnnotatedTree[N, A]) => loopWhile0(tree)
  }

  def drop1[N, A, B, E]: Analysis[N, (A, B), B, E] = (tree) => Validation.success(tree.annotate(n => tree.attr(n)._2))

  def take1[N, A, B, E]: Analysis[N, (A, B), A, E] = (tree) => Validation.success(tree.annotate(n => tree.attr(n)._1))

  def flip[N, A, B, E]: Analysis[N, (A, B), (B, A), E] = (tree) => Validation.success(tree.annotate(n => (tree.attr(n)._2, tree.attr(n)._1)))
}