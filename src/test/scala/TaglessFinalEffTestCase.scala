import cats.Monad
import org.scalatest.FlatSpec
import cats.implicits._
import cats.data.{State, Writer}
import org.atnos.eff.{Eff, Fx, |=}
import shapeless.tag
import shapeless.tag.@@

import scala.language.higherKinds

class TaglessFinalEffTestCase extends FlatSpec {
    "Use Eff to mix difference Tagless Final Dsl" should "" in {
        /**
         * 该例子演示如何将不同的 Monad 容器结合在一起. 该例子实现了连个 DSL, 分别是 State(作用于 AuthDsl) 和 Writer( 作用于 EmailDsl)，
         * 然后通过 Eff monad 结合起来．
         *
         * 参考：
         * 　　https://www.becompany.ch/en/blog/2018/09/27/tagless-final-and-eff
         * 　　https://github.com/becompany/tagless-final-eff-example
         * */

        sealed trait EmailAddress
        final case class User(email: String @@ EmailAddress, password: String)

        sealed trait Error
        final case class RegistrationError(message:String) extends Error
        final case class AuthError(message:String) extends Error

        /**
         * Dsl 本身的定义和 TaglessFinalTestCase 没有什么不同，可以完全照搬．
         * */
        trait AuthDsl[F[_]] {
            def register(email: String @@ EmailAddress, password: String): F[Either[RegistrationError, User]]
            def auth(email: String @@ EmailAddress, password: String): F[Either[AuthError, User]]
        }

        object AuthDsl {
            type UserRepository = List[User]
            type UserRepositoryState[A] = State[UserRepository, A]

            implicit def dslInterpreter: AuthDsl[UserRepositoryState] = new AuthDsl[UserRepositoryState] {
                override def register(email: String @@ EmailAddress, password: String): UserRepositoryState[Either[RegistrationError, User]] =
                    State { users =>
                        if (users.exists(_.email === email))
                            (users, RegistrationError("User already exists").asLeft)
                        else {
                            val user = User(email, password)
                            (users :+ user, user.asRight)
                        }
                    }

                override def auth(email: String @@ EmailAddress, password: String): UserRepositoryState[Either[AuthError, User]] =
                    State.inspect(
                        _.find(user => user.email === email && user.password == password)
                                .toRight(AuthError("Authentication failed")))
            }

            /**
             * 1）
             * 由此开始添加 Eff interpreter. Eff 的作用是作为 dslInterpreter 的代理，它为代理的 Monad 加装上一个统一的外壳，
             * 这样就可以被一起放入 for-comprehension 了。Eff 是基于 Free monad 的（关于 free 和 tagless 结合的参考文章:
             * https://softwaremill.com/free-tagless-compared-how-not-to-commit-to-monad-too-early/）。它通过 `send()`
             * 函数将请求转发到被代理的目标 interpreter。
             *
             * 在 Eff 中，Dsl 的执行容器（Monad）被称为 `effects`, 所有需要被代理的 Monad 的合集被称为 `effect stack`。
             *
             * Eff 定义了两个参数 Eff[R, A]，第一个参数 `R` 代表最终生成的 effect stack 的类型，在这个例子中，它将包含 State 和 Writer.
             * 一般来说你需要为特定 Monad 实现载入动作，但是 Eff 缺省支持了大部分 Cats 的 Monad，因此我们可以省去许多麻烦，因此我们
             * 只需要为函数添加隐式参数　`implicit evidence: F |= R` 来提供一个证明（evidence）这个 Monad 属于 R，这个证明的实现
             * 将放在最后。先看一下　`|=` 的定义：
             *
             *   type |=[F[_], R] = MemberIn.|=[F, R]
             *
             * 显然它只是个中缀运算符，并且顾名思义它表示将 Monad F 载入为 R 的成员。
             *
             *   `A` 则是返回值类型（在这里就是）。
             *
             * Eff[R, _] 将代替 F[_] 成为我们代理的 Dsl 的类型参数。为此我们需要使用类型投影来将第二个参数 A 投影到 F 容器中。
             * 为了简化代码，我们使用一个小技巧：通配符 `?` 来实现类型投影，要使用 `?` 我们需要在 `build.sbt` 中添加以下参数:
             *
             *   scalacOptions += "-Ypartial-unification"
             *   addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
             *
             * 好了，现在一切就绪，eff interpreter 代理的定义如下, 它有以下几点特征：
             *   a. effInterpreter 作为 dslInterpreter 的代理，它将自己`伪装`成 AuthDsl，但是参数是复合 Monad（Eff[R,?]）以提供统一容器界面。
             *   b. 参数 evidence:F |= R 会被（隐式）传递给 send() 用于生成 effect stack
             *   c. 请求最终传递给 dslInterpreter，返回值容器`F` 同样替换成 `Eff`。并且通过类型投影将最终返回值装入。
             **/
            private def effInterpreter[R, F[_]](interpreter: AuthDsl[F])
                                               (implicit evidence: F |= R): AuthDsl[Eff[R, ?]] = new AuthDsl[Eff[R, ?]] {
                override def register(email: String @@ EmailAddress, password: String): Eff[R, Either[RegistrationError, User]] =
                    Eff.send(interpreter.register(email, password))

                override def auth(email: String @@ EmailAddress, password: String): Eff[R, Either[AuthError, User]] =
                    Eff.send(interpreter.auth(email, password))
            }

            /**
             * 2）
             * 现在，我们要为 AuthDsl 准备的容器 UserRepositoryState（F）属于 R 提供一个证明. 我们可以利用 context bound，将这个
             * 证明声明为类型参数：
             * */
            type _userRepositoryState[R] = UserRepositoryState |= R

            /**
             * 3）
             * effDslInterpreter 通过将 _userRepositoryState 声明为 context bound 来使之成为隐式．并且它还将返回的 effInterpreter
             * 也声明为隐式以便编译器动态选择。
             */
            implicit def effDslInterpreter[R: _userRepositoryState]: AuthDsl[Eff[R, ?]] =
                effInterpreter[R, UserRepositoryState](dslInterpreter)
        }

        /**
         * 将同样的过程应用于另一个 Email DSL
         * */

        final case class Email(to: String @@ EmailAddress, subject: String, body: String)

        trait EmailDsl[F[_]] {
            def send(to: String @@ EmailAddress, subject: String, body: String): F[Unit]
        }

        object EmailDsl {
            /**
             * 假设 Email DSL 的容器是 Writer
             * */
            type EMailWriter[A] = Writer[Email, A]

            lazy val emailInterpreter: EmailDsl[EMailWriter] = new EmailDsl[EMailWriter] {
                    override def send(to: @@[String, EmailAddress], subject: String, body: String): EMailWriter[Unit] =
                        Writer.tell(Email(to, subject, body))
                }

            private def effInterpreter[R, F[_]](interpreter: EmailDsl[F]) (implicit evidence: F |= R): EmailDsl[Eff[R, ?]] = new EmailDsl[Eff[R, ?]] {
                    override def send(to: @@[String, EmailAddress], subject: String, body: String): Eff[R, Unit] =
                        Eff.send(interpreter.send(to, subject, body))
                }

            type _mailQueue[R] = EMailWriter |= R

            implicit def effWriterInterpreter[R : _mailQueue]: EmailDsl[Eff[R, ?]] = effInterpreter[R, EMailWriter](emailInterpreter)
        }

        /**
         * 4）现在我们同时拥有两个 Dsl，我们可以在 for-comprehension 中组合使用它们。
         * */
        object Service {
            /** 隐式参数实际上传入各自的 Eff interpreter 代理。 */
            def registerAndLogin[F[_] : Monad](implicit authnDsl: AuthDsl[F], emailDsl: EmailDsl[F]): F[Either[AuthError, User]] = {
                val email = tag[EmailAddress]("john@doe.com")
                val password = "swordfish"

                for {
                    _ <- authnDsl.register(email, password)
                    _ <- emailDsl.send(email, "Hello", "Thank you for registering")
                    authenticated <- authnDsl.auth(email, password)
                } yield authenticated
            }
        }

        /**
         * 5) 最后我们还需要生成 UserRepositoryState 和 MailWriter 的组合容器
         * */
        import AuthDsl.UserRepositoryState
        import EmailDsl.EMailWriter
        type Stack = Fx.fx2[UserRepositoryState, EMailWriter]
        type Result[A] = Eff[Stack, A]

        /**
         * 6) 执行运算
         *
         * 因为组合容器包含 State 和 Writer，它们都需要传入参数．Eff 提供隐式函数来支持,引入 org.atnos.eff.all._ 和 org.atnos.eff.syntax.all._
         * 然后我们可以对组合函数调用 runState 和 runWriterFold 函数来传入参数:
         * */
        import Service._
        import org.atnos.eff.all._
        import org.atnos.eff.syntax.all._
        val _comp = registerAndLogin[Result]

        /**
         * Bug: 因为 Eff 还是基于 Cats 0.9 版本. 1.0 以后的版本 Cartesian 已经更名为 Semigroupal, 因此如果指定高版本 Cats
         * 会遇到以下错误：
         *
         *   java.lang.ClassNotFoundException: cats.Cartesian
         * */
        import cats.Cartesian
        val res = _comp
                .runState(List.empty[User])
                .runWriterFold(ListFold[Email])
                .map{
                    case ((authenticated, users), emails) =>
                        // 返回值也按执行顺序
                        authenticated.values match {
                            case Right(user) =>
                                assert(user.email === tag[EmailAddress]("john@doe.com"))
                            case Left(_) =>
                                ???
                        }
                }.run
    }
}
