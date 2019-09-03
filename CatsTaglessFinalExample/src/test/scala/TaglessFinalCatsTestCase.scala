import cats.arrow.FunctionK
import cats.{Monad, ~>}
import org.scalatest.FlatSpec
import cats.implicits._
import cats.data.{State, StateT, Writer}
import cats.effect.IO
import cats.tagless.autoFunctorK
import shapeless.tag
import shapeless.tag.@@

class TaglessFinalCatsTestCase extends FlatSpec{
    /**
     * Cats 也提供了 Tagless final 支持(https://typelevel.org/cats-tagless/)
     * */
    "Use Cats-tagless for single Dsl" should "" in {
        /**
         * 用常规方法实现 Tagless Dsl
         * */
        // AuthDsl
        sealed trait EmailAddress
        final case class User(email: String @@ EmailAddress, password: String)

        sealed trait Error
        final case class RegistrationError(message:String) extends Error
        final case class AuthError(message:String) extends Error

        /**
         * 1）声明 `@autoFunctorK`。对于普通的 Tagless Final 则没必要（２a）。`@autoFunctorK` 自动生成 Free 代理和 evidence
         * （参见 Eff 例子的说明），在使用的时候只要　`import AuthDsl.autoDerive._` 即可获得需要的一切。（参考 2b 处的说明。）
         *
         *  为了使用 autoXXX 宏，需要在 build.sbt 中引入:
         *      "org.typelevel" %% "cats-tagless-macros" % Version
         * */
        @autoFunctorK
        trait AuthDsl[F[_]] {
            def register(email: String @@ EmailAddress, password: String): F[Either[RegistrationError, User]]
            def auth(email: String @@ EmailAddress, password: String): F[Either[AuthError, User]]
        }

        object AuthDsl {
            type UserRepository = List[User]
            type UserRepositoryState[A] = StateT[IO, UserRepository, A]
            /**
             * 实现 interpreter，和 Eff 的例子差不多，但是必须声明为隐式。
             * */
            implicit object AuthDslInterpreter extends AuthDsl[UserRepositoryState] {
                override def register(email: String @@ EmailAddress, password: String): UserRepositoryState[Either[RegistrationError, User]] =
                    StateT { users => IO{
                        if (users.exists(_.email === email))
                            (users, RegistrationError("User already exists").asLeft)
                        else {
                            val user = User(email, password)
                            (users :+ user, user.asRight)
                        }
                    } }

                override def auth(email: String @@ EmailAddress, password: String): UserRepositoryState[Either[AuthError, User]] =
                    StateT.inspect(
                        _.find(user => user.email === email && user.password == password)
                                .toRight(AuthError("Authentication failed")))
            }
        }

        /* 服务 */
        object Service {
            def registerAndLogin[F[_]: Monad](implicit dsl: AuthDsl[F]): F[Either[AuthError, User]] = {
                val email = tag[EmailAddress]("john@doe.com")
                val password = "swordfish"

                /**
                 * import cats.implicits._
                 * */
                for {
                    _ <- dsl.register(email, password)
                    authenticated <- dsl.auth(email, password)
                } yield authenticated
            }
        }

        /* 使用 */
        import Service._
        import AuthDsl.UserRepositoryState

        /**
         * 2a) 一般来讲，对于一个普通的 Tagless Final，完成了定义就可以直接使用了。(通过 `UserRepositoryState` 告知编译器选择哪个 interpreter)
         * */
        registerAndLogin[UserRepositoryState].run(List.empty[User]).unsafeRunSync().map{
            case Right(user) =>
                assert(user.email === tag[EmailAddress]("john@doe.com"))
            case Left(_) =>
                ???
        }

        /**
         * 2b) 但是对于存在以下需求的 Tagless Final：
         *
         *     需要结合多种不同的 dsl monad。和 Eff 类似，Cats tagless 也是通过 Dsl 提供一个代理层来实现 Monad 组合，不同的是
         *     Cats 的这个代理层由 `@autoFunctorK` 自动生成，这就是为什么需要在 trait 签名上声明 `@autoFunctorK`。这个宏将自动
         *     生成代理和代理需要的 evidence，
         *
         *     有堆栈安全的需求。`@autoFunctorK` 还生成 Free[A, ?] 隐式代理。
         *
         * 对于多个不同的 dsl monad 的需求参考 TaglessFinalMultipleCatsTestCase。本例演示单个 Dsl 的 Free 需求。
         * */
        import AuthDsl.autoDerive._

        /**
         * 还需要申明 Free.liftF。（将它声明为隐式）
         * */
        import cats.free.Free
        implicit def toFree[F[_]]: F ~> Free[F, ?] = λ[F ~> Free[F, ?]](t => Free.liftF(t))

        /**
         * 现在可以进行堆栈安全的调用了。（忽略支持 Free 类型参数的 AutoDsl 隐式实例找不到的警告，autoDerive 将为我们动态生成它）
         * */
        registerAndLogin[Free[UserRepositoryState, ?]].foldMap(FunctionK.id).run(List.empty[User]).unsafeRunSync().map{
            case Right(user) =>
                assert(user.email === tag[EmailAddress]("john@doe.com"))
            case Left(_) =>
                ???
        }
    }
}
