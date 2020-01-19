@file:Suppress("EXPERIMENTAL_API_USAGE", "MemberVisibilityCanBePrivate", "unused")

package net.mamoe.mirai.event

import kotlinx.coroutines.CoroutineScope
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.isAdministrator
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.contact.isOwner
import net.mamoe.mirai.message.FriendMessage
import net.mamoe.mirai.message.GroupMessage
import net.mamoe.mirai.message.MessagePacket
import net.mamoe.mirai.message.data.Message
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * 订阅来自所有 [Bot] 的所有联系人的消息事件. 联系人可以是任意群或任意好友或临时会话.
 */
@UseExperimental(ExperimentalContracts::class)
@MessageDsl
inline fun CoroutineScope.subscribeMessages(crossinline listeners: MessageSubscribersBuilder<MessagePacket<*, *>>.() -> Unit) {
    contract {
        callsInPlace(listeners, InvocationKind.EXACTLY_ONCE)
    }
    MessageSubscribersBuilder<MessagePacket<*, *>> { listener ->
        subscribeAlways {
            listener(it, this.message.toString())
        }
    }.apply { listeners() }
}

/**
 * 订阅来自所有 [Bot] 的所有群消息事件
 */
@UseExperimental(ExperimentalContracts::class)
@MessageDsl
inline fun CoroutineScope.subscribeGroupMessages(crossinline listeners: MessageSubscribersBuilder<GroupMessage>.() -> Unit) {
    contract {
        callsInPlace(listeners, InvocationKind.EXACTLY_ONCE)
    }
    MessageSubscribersBuilder<GroupMessage> { listener ->
        subscribeAlways {
            listener(it, this.message.toString())
        }
    }.apply { listeners() }
}

/**
 * 订阅来自所有 [Bot] 的所有好友消息事件
 */
@UseExperimental(ExperimentalContracts::class)
@MessageDsl
inline fun CoroutineScope.subscribeFriendMessages(crossinline listeners: MessageSubscribersBuilder<FriendMessage>.() -> Unit) {
    contract {
        callsInPlace(listeners, InvocationKind.EXACTLY_ONCE)
    }
    MessageSubscribersBuilder<FriendMessage> { listener ->
        subscribeAlways {
            listener(it, this.message.toString())
        }
    }.apply { listeners() }
}

/**
 * 订阅来自这个 [Bot] 的所有联系人的消息事件. 联系人可以是任意群或任意好友或临时会话.
 */
@UseExperimental(ExperimentalContracts::class)
@MessageDsl
inline fun Bot.subscribeMessages(crossinline listeners: MessageSubscribersBuilder<MessagePacket<*, *>>.() -> Unit) {
    contract {
        callsInPlace(listeners, InvocationKind.EXACTLY_ONCE)
    }
    MessageSubscribersBuilder<MessagePacket<*, *>> { listener ->
        this.subscribeAlways {
            listener(it, this.message.toString())
        }
    }.apply { listeners() }
}

/**
 * 订阅来自这个 [Bot] 的所有群消息事件
 */
@UseExperimental(ExperimentalContracts::class)
@MessageDsl
inline fun Bot.subscribeGroupMessages(crossinline listeners: MessageSubscribersBuilder<GroupMessage>.() -> Unit) {
    contract {
        callsInPlace(listeners, InvocationKind.EXACTLY_ONCE)
    }
    MessageSubscribersBuilder<GroupMessage> { listener ->
        this.subscribeAlways {
            listener(it, this.message.toString())
        }
    }.apply { listeners() }
}

/**
 * 订阅来自这个 [Bot] 的所有好友消息事件.
 */
@UseExperimental(ExperimentalContracts::class)
@MessageDsl
inline fun Bot.subscribeFriendMessages(crossinline listeners: MessageSubscribersBuilder<FriendMessage>.() -> Unit) {
    contract {
        callsInPlace(listeners, InvocationKind.EXACTLY_ONCE)
    }
    MessageSubscribersBuilder<FriendMessage> { listener ->
        this.subscribeAlways {
            it.listener(it.message.toString())
        }
    }.apply { listeners() }
}


typealias MessageListener<T> = @MessageDsl suspend T.(String) -> Unit

/**
 * 消息订阅构造器
 *
 * @see subscribeFriendMessages
 * @sample demo.subscribe.messageDSL
 */
// TODO: 2019/12/23 应定义为 inline, 但这会导致一个 JVM run-time VerifyError. 等待 kotlin 修复 bug (Kotlin 1.3.61)
@Suppress("unused")
@MessageDsl
class MessageSubscribersBuilder<T : MessagePacket<*, *>>(
    val subscriber: (MessageListener<T>) -> Listener<T>
) {
    /**
     * 监听的条件
     */
    inner class ListeningFilter(
        val filter: T.(String) -> Boolean
    ) {
        /**
         * 进行逻辑 `or`.
         */
        infix fun or(another: ListeningFilter): ListeningFilter =
            ListeningFilter { filter.invoke(this, it) || another.filter.invoke(this, it) }

        /**
         * 进行逻辑 `and`.
         */
        infix fun and(another: ListeningFilter): ListeningFilter =
            ListeningFilter { filter.invoke(this, it) && another.filter.invoke(this, it) }

        /**
         * 进行逻辑 `xor`.
         */
        infix fun xor(another: ListeningFilter): ListeningFilter =
            ListeningFilter { filter.invoke(this, it) xor another.filter.invoke(this, it) }

        /**
         * 进行逻辑 `nand`, 即 `not and`.
         */
        infix fun nand(another: ListeningFilter): ListeningFilter =
            ListeningFilter { !filter.invoke(this, it) || !another.filter.invoke(this, it) }

        /**
         * 启动时间监听.
         */
        // do not inline due to kotlin (1.3.61) bug: java.lang.IllegalAccessError
        operator fun invoke(onEvent: MessageListener<T>): Listener<T> {
            return content(filter, onEvent)
        }
    }

    /**
     * 无任何触发条件.
     */
    @MessageDsl
    fun always(onEvent: MessageListener<T>): Listener<T> = subscriber(onEvent)

    /**
     * 如果消息内容 `==` [equals]
     * @param trim `true` 则删除首尾空格后比较
     * @param ignoreCase `true` 则不区分大小写
     */
    @MessageDsl
    inline fun case(
        equals: String,
        ignoreCase: Boolean = false,
        trim: Boolean = true,
        crossinline onEvent: @MessageDsl suspend T.(String) -> Unit
    ): Listener<T> {
        val toCheck = if (trim) equals.trim() else equals
        return content({ (if (trim) it.trim() else it).equals(toCheck, ignoreCase = ignoreCase) }, {
            onEvent(this, this.message.toString())
        })
    }

    /**
     * 如果消息内容包含 [sub]
     */
    @MessageDsl
    fun contains(sub: String): ListeningFilter =
        content { sub in it }

    /**
     * 如果消息内容包含 [sub]
     */
    inline fun contains(
        sub: String,
        ignoreCase: Boolean = false,
        trim: Boolean = true,
        crossinline onEvent: MessageListener<T>
    ): Listener<T> {
        val toCheck = if (trim) sub.trim() else sub
        return content({ (if (trim) it.trim() else it).contains(toCheck, ignoreCase = ignoreCase) }, {
            onEvent(this, this.message.toString())
        })
    }

    /**
     * 如果消息的前缀是 [prefix]
     */
    @MessageDsl
    inline fun startsWith(
        prefix: String,
        removePrefix: Boolean = true,
        trim: Boolean = true,
        crossinline onEvent: @MessageDsl suspend T.(String) -> Unit
    ): Listener<T> {
        val toCheck = if (trim) prefix.trim() else prefix
        return content({ (if (trim) it.trim() else it).startsWith(toCheck) }, {
            if (removePrefix) this.onEvent(this.message.toString().substringAfter(toCheck))
            else onEvent(this, this.message.toString())
        })
    }

    /**
     * 如果消息的结尾是 [suffix]
     */
    @MessageDsl
    fun endsWith(suffix: String): ListeningFilter =
        content { it.endsWith(suffix) }

    /**
     * 如果是这个人发的消息. 消息可以是好友消息也可以是群消息
     */
    @MessageDsl
    fun sentBy(qqId: Long): ListeningFilter =
        content { sender.id == qqId }

    /**
     * 如果是这个人发的消息. 消息可以是好友消息也可以是群消息
     */
    @MessageDsl
    inline fun sentBy(qqId: Long, crossinline onEvent: MessageListener<T>): Listener<T> =
        content({ this.sender.id == qqId }, onEvent)

    /**
     * 如果是管理员或群主发的消息
     */
    @MessageDsl
    fun sentByOperator(): ListeningFilter =
        content { this is GroupMessage && sender.permission.isOperator() }

    /**
     * 如果是管理员或群主发的消息
     */
    @MessageDsl
    inline fun sentByOperator(crossinline onEvent: MessageListener<T>): Listener<T> =
        content({ this is GroupMessage && this.sender.isOperator() }, onEvent)

    /**
     * 如果是管理员发的消息
     */
    @MessageDsl
    fun sentByAdministrator(): ListeningFilter =
        content { this is GroupMessage && sender.permission.isAdministrator() }

    /**
     * 如果是管理员发的消息
     */
    @MessageDsl
    inline fun sentByAdministrator(crossinline onEvent: MessageListener<T>): Listener<T> =
        content({ this is GroupMessage && this.sender.isAdministrator() }, onEvent)

    /**
     * 如果是群主发的消息
     */
    @MessageDsl
    fun sentByOwner(): ListeningFilter =
        content { this is GroupMessage && sender.isOwner() }

    /**
     * 如果是群主发的消息
     */
    @MessageDsl
    inline fun sentByOwner(crossinline onEvent: MessageListener<T>): Listener<T> =
        content({ this is GroupMessage && this.sender.isOwner() }, onEvent)

    /**
     * 如果是来自这个群的消息
     */
    @MessageDsl
    fun sentFrom(groupId: Long): ListeningFilter =
        content { this is GroupMessage && group.id == groupId }

    /**
     * 如果是来自这个群的消息, 就执行 [onEvent]
     */
    @MessageDsl
    inline fun sentFrom(groupId: Long, crossinline onEvent: MessageListener<T>): Listener<T> =
        content({ this is GroupMessage && this.group.id == groupId }, onEvent)

    /**
     * 如果消息内容包含 [M] 类型的 [Message]
     */
    @MessageDsl
    inline fun <reified M : Message> has(): ListeningFilter =
        content { message.any { it::class == M::class } }

    /**
     * 如果消息内容包含 [M] 类型的 [Message], 就执行 [onEvent]
     */
    @MessageDsl
    inline fun <reified M : Message> has(crossinline onEvent: MessageListener<T>): Listener<T> =
        content({ message.any { it::class == M::class } }, onEvent)

    /**
     * 如果 [filter] 返回 `true`
     */
    @MessageDsl
    inline fun content(crossinline filter: T.(String) -> Boolean): ListeningFilter =
        ListeningFilter { filter(this, it) }

    /**
     * 如果 [filter] 返回 `true` 就执行 `onEvent`
     */
    @MessageDsl
    inline fun content(crossinline filter: T.(String) -> Boolean, crossinline onEvent: MessageListener<T>): Listener<T> =
        subscriber {
            if (filter(this, it)) onEvent(this, it)
        }

    /**
     * 如果消息内容可由正则表达式匹配([Regex.matchEntire]), 就执行 `onEvent`
     */
    @MessageDsl
    fun matching(regex: Regex): ListeningFilter =
        content { regex.matchEntire(it) != null }

    /**
     * 如果 [filter] 返回 `true` 就执行 `onEvent`
     */
    @MessageDsl
    inline fun matching(regex: Regex, crossinline onEvent: MessageListener<T>): Listener<T> =
        content({ regex.matchEntire(it) != null }, onEvent)

    /**
     * 如果消息内容可由正则表达式查找([Regex.find]), 就执行 `onEvent`
     */
    @MessageDsl
    fun finding(regex: Regex): ListeningFilter =
        content { regex.find(it) != null }


    /**
     * 若消息内容包含 [this] 则回复 [reply]
     */
    @MessageDsl
    infix fun String.containsReply(reply: String): Listener<T> =
        content({ this@containsReply in it }, { reply(reply) })

    /**
     * 若消息内容包含 [this] 则执行 [replier] 并将其返回值回复给发信对象.
     *
     * [replier] 的 `it` 将会是消息内容 string.
     *
     * @param replier 若返回 [Message] 则直接发送; 若返回 [Unit] 则不回复; 其他情况则 [Any.toString] 后回复
     */
    @MessageDsl
    inline infix fun String.containsReply(crossinline replier: @MessageDsl suspend T.(String) -> Any?): Listener<T> =
        content({ this@containsReply in it }, {
            @Suppress("DSL_SCOPE_VIOLATION_WARNING")
            this.executeAndReply(replier)
        })

    /**
     * 若消息内容可由正则表达式匹配([Regex.matchEntire]), 则执行 [replier] 并将其返回值回复给发信对象.
     *
     * [replier] 的 `it` 将会是消息内容 string.
     *
     * @param replier 若返回 [Message] 则直接发送; 若返回 [Unit] 则不回复; 其他情况则 [Any.toString] 后回复
     */
    @MessageDsl
    inline infix fun Regex.matchingReply(crossinline replier: @MessageDsl suspend T.(String) -> Any?): Listener<T> =
        content({ this@matchingReply.matchEntire(it) != null }, {
            @Suppress("DSL_SCOPE_VIOLATION_WARNING")
            this.executeAndReply(replier)
        })

    /**
     * 若消息内容可由正则表达式查找([Regex.find]), 则执行 [replier] 并将其返回值回复给发信对象.
     *
     * [replier] 的 `it` 将会是消息内容 string.
     *
     * @param replier 若返回 [Message] 则直接发送; 若返回 [Unit] 则不回复; 其他情况则 [Any.toString] 后回复
     */
    @MessageDsl
    inline infix fun Regex.findingReply(crossinline replier: @MessageDsl suspend T.(String) -> Any?): Listener<T> =
        content({ this@findingReply.find(it) != null }, {
            @Suppress("DSL_SCOPE_VIOLATION_WARNING")
            this.executeAndReply(replier)
        })

    /**
     * 不考虑空格, 若消息内容以 [this] 开始则执行 [replier] 并将其返回值回复给发信对象.
     *
     * [replier] 的 `it` 将会是去掉用来判断的前缀并删除前后空格后的字符串.
     * 如当消息为 "kick    123456     " 时
     * ```kotlin
     * "kick" startsWithReply {
     *     println(it) // it 为 "123456"
     * }
     * ```
     *
     * @param replier 若返回 [Message] 则直接发送; 若返回 [Unit] 则不回复; 其他类型则 [Any.toString] 后回复
     */
    @MessageDsl
    inline infix fun String.startsWithReply(crossinline replier: @MessageDsl suspend T.(String) -> Any?): Listener<T> {
        val toCheck = this.trimStart()
        return content({ it.trim().startsWith(toCheck) }, {
            @Suppress("DSL_SCOPE_VIOLATION_WARNING")
            this.executeAndReply {
                replier(this, it.trim().removePrefix(toCheck))
            }
        })
    }

    /**
     * 不考虑空格, 若消息内容以 [this] 结尾则执行 [replier] 并将其返回值回复给发信对象.
     *
     * [replier] 的 `it` 将会是去掉用来判断的后缀并删除前后空格后的字符串.
     * 如当消息为 "  123456 test" 时
     * ```kotlin
     * "test" endswithReply {
     *     println(it) // it 为 "123456"
     * }
     * ```
     *
     * @param replier 若返回 [Message] 则直接发送; 若返回 [Unit] 则不回复; 其他情况则 [Any.toString] 后回复
     */
    @MessageDsl
    inline infix fun String.endswithReply(crossinline replier: @MessageDsl suspend T.(String) -> Any?): Listener<T> {
        val toCheck = this.trimEnd()
        return content({ it.trim().endsWith(toCheck) }, {
            @Suppress("DSL_SCOPE_VIOLATION_WARNING")
            this.executeAndReply {
                replier(this, it.trim().removeSuffix(toCheck))
            }
        })
    }

    @MessageDsl
    infix fun String.reply(reply: String): Listener<T> {
        val toCheck = this.trim()
        return content({ it.trim() == toCheck }, { reply(reply) })
    }

    @MessageDsl
    inline infix fun String.reply(crossinline replier: @MessageDsl suspend T.(String) -> Any?): Listener<T> {
        val toCheck = this.trim()
        return content({ it.trim() == toCheck }, {
            @Suppress("DSL_SCOPE_VIOLATION_WARNING")
            this.executeAndReply {
                replier(this, it.trim())
            }
        })
    }

    @PublishedApi
    @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE") // false positive
    internal suspend inline fun T.executeAndReply(replier: suspend T.(String) -> Any?) {
        when (val message = replier(this, this.message.toString())) {
            is Message -> this.reply(message)
            is Unit -> {

            }
            else -> this.reply(message.toString())
        }
    }

/* 易产生迷惑感
 fun replyCase(equals: String, trim: Boolean = true, replier: MessageReplier<T>) = case(equals, trim) { reply(replier(this)) }
 fun replyContains(value: String, replier: MessageReplier<T>) = content({ value in it }) { replier(this) }
 fun replyStartsWith(value: String, replier: MessageReplier<T>) = content({ it.startsWith(value) }) { replier(this) }
 fun replyEndsWith(value: String, replier: MessageReplier<T>) = content({ it.endsWith(value) }) { replier(this) }
*/
}

/**
 * DSL 标记. 将能让 IDE 阻止一些错误的方法调用.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@DslMarker
internal annotation class MessageDsl