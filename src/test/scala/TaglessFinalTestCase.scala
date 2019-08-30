import cats._
import cats.implicits._
import cats.data.State
import org.scalatest.FlatSpec
import shapeless.tag.@@
import shapeless.tag

class TaglessFinalTestCase extends FlatSpec{
    /**
     * DSL 通常需要我们返回 Monad 以便可以用于 for-comprehension, Tagless Final 则允许我们以更接近传统的方式定义函数.它带来以下好处:
     *
     * 1) Monad 的定义可以在函数完成之后.
     * 2) 可以根据不同的 interpreter 灵活定义 Monad,比如可以在测试和正式环境中使用不同的数据源
     * 3) 允许混合不同的 Monad
     *
     * 参考：
     * https://www.becompany.ch/en/blog/2018/06/21/tagless-final
     * https://github.com/becompany/tagless-final-example
     * */

    "Tagless Final" should "" in {
        /**
         * 该例子将实现两个帐户管理方法, 一个用于注册(register), 另一个用于认证(auth)
         */
        /* 定义 ADT */
        sealed trait EmailAddress
        case class User(email: String @@ EmailAddress, password: String)

        sealed trait Error
        case class RegistrationError(message:String) extends Error
        case class AuthError(message:String) extends Error
        /**
         * 1) 定义 DSL
         *
         * Tagless final 模式下，DSL 通过带有单个类型参数的 `F[_]` 的 trait 来建模。这个 `F[_]` 就是未来我们在使用
         * DSL时才决定的容器
         * */
        trait Dsl[F[_]] {
            /**
             * 在定义 DSL 时将我们只需想对待普通函数一样考虑其返回类型．不同的是我们只需要将它，比如 `Either[RegistrationError, User]`
             * 装入 F[] 中即可，而无须此时就考虑将其写成某种特定的 Monad 形式．
             *  */
            // `@@` 是 Tagged type（参考: TaggedTypeTestCase），这里用来约束特定格式的字符串。
            import shapeless.tag.@@
            def register(email: String @@ EmailAddress, password: String): F[Either[RegistrationError, User]]
            def auth(email: String @@ EmailAddress, password: String): F[Either[AuthError, User]]
        }

        object Dsl {
            /**
             * 2) 接下来我们需要思考一个恰当的容器来作为 F[_] 的值．此时我们考虑 Dsl 函数应该存在的合理状态．很显然因为我们的 dsl
             * 都需要和数据库打交道，而数据库是存在状态的，因此一个 State Monad 是一个比较好的选择．也就是说比如 register 函数按
             * 照传统方式声明，它的合理签名因该是:
             *
             * def register(
             *      email: String @@ EmailAddress,
             *      password: String
             * ): State[Repository, Either[RegistrationError, User]]
             *
             * 那么 F[_] 就应该是 State[Repository, _]．F 的参数预留给函数的实际返回值类型．如此变化的结果只是将容器的设计独立
             * 出 dsl 的设计过程．
             *
             * 同时假设我们在测试环境中用 List 来存储用户．考虑这一点非常重要，因为一旦我们的 F，也就是 State Monad 绑定了特定的
             * Repository 类型，它就可以被隐式应用于不同的环境．（我们将在下面看到）
             */
            type UserRepository = List[User]
            type UserRepositoryState[A] = State[UserRepository, A]

            /**
             * 3）实现 interpreter(测试环境).
             *
             * 在测试环境实现 interpreter 的时候，我们将 UserRepositoryState 作为 F 的值提供给 Dsl. 注意此时因为这个 interpreter
             * 携带了 UserRepository 的类型信息. 我们将其设为隐式以任由 runtime 去选择.
             * */
            implicit object DslInterpreter extends Dsl[UserRepositoryState] {
                /**
                 * 很显然，加持在 Repository 上的 State monad 也就成了返回值的容器。
                 * */
                override def register(
                                             email: String @@ EmailAddress,
                                             password: String
                                     ): UserRepositoryState[Either[RegistrationError, User]] = {
                    State { users =>
                        if (users.exists(_.email === email))
                            (users, RegistrationError("User already exists").asLeft)
                        else {
                            val user = User(email, password)
                            (users :+ user, user.asRight)
                        }
                    }
                }

                override def auth(email: String @@ EmailAddress, password: String): UserRepositoryState[Either[AuthError, User]] =
                    State.inspect(
                        _.find(user => user.email === email && user.password == password)
                                .toRight(AuthError("Authentication failed")))
            }
        }
        /**
         * 4) 应用 Tagless final dsl
         * */
        object Service {
            /**
             * 设计客户端的时候, 我们确保函数声明 F[_] 为 Monad[F[_]](context bound 语法). 然后注意隐式的用法，我们希望客户端在运行
             * 时根据 F 参数的值自动选择使用哪个实例, 这样我们就可以为测试环境和生产环境匹配不同的 interpreter 了
             * */
            def registerAndLogin[F[_]: Monad](implicit dsl: Dsl[F]): F[Either[AuthError, User]] = {
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

        /**
         * 5) 执行的时候指定编译器使用特定的 Monad 作为 F 容器。当然这个值也会被 implicit dsl: Dsl[F] 用于去寻找到 DslInterpreter。
         * */
        import Service._
        import Dsl.UserRepositoryState
        registerAndLogin[UserRepositoryState].run(List.empty[User]).value.map{
            case Right(user) =>
                assert(user.email === tag[EmailAddress]("john@doe.com"))
            case Left(_) =>
                ???
        }
    }
}
