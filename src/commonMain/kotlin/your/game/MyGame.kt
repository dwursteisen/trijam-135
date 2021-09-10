package your.game

import com.curiouscreature.kotlin.math.Mat4
import com.curiouscreature.kotlin.math.min
import com.curiouscreature.kotlin.math.translation
import com.github.dwursteisen.minigdx.GameContext
import com.github.dwursteisen.minigdx.Seconds
import com.github.dwursteisen.minigdx.ecs.Engine
import com.github.dwursteisen.minigdx.ecs.components.Component
import com.github.dwursteisen.minigdx.ecs.components.HorizontalAlignment
import com.github.dwursteisen.minigdx.ecs.components.TextComponent
import com.github.dwursteisen.minigdx.ecs.components.text.WriteText
import com.github.dwursteisen.minigdx.ecs.entities.Entity
import com.github.dwursteisen.minigdx.ecs.entities.EntityFactory
import com.github.dwursteisen.minigdx.ecs.entities.position
import com.github.dwursteisen.minigdx.ecs.events.Event
import com.github.dwursteisen.minigdx.ecs.physics.AABBCollisionResolver
import com.github.dwursteisen.minigdx.ecs.systems.EntityQuery
import com.github.dwursteisen.minigdx.ecs.systems.System
import com.github.dwursteisen.minigdx.ecs.systems.TemporalSystem
import com.github.dwursteisen.minigdx.file.Font
import com.github.dwursteisen.minigdx.file.Texture
import com.github.dwursteisen.minigdx.file.get
import com.github.dwursteisen.minigdx.game.Game
import com.github.dwursteisen.minigdx.graph.GraphScene
import com.github.dwursteisen.minigdx.imgui.ImGuiSystem
import com.github.dwursteisen.minigdx.input.Key
import com.github.dwursteisen.minigdx.math.ImmutableVector3
import com.github.dwursteisen.minigdx.math.Interpolations
import com.github.minigdx.imgui.WidgetBuilder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class Player : Component
class Circle(var touched: Boolean = false) : Component
class Square(var touched: Boolean = false) : Component
class Movable(var speed: Float = -4f) : Component

class Generator : Component
class Hit : Component
class Destroy : Component
class Score : Component
class Disappear(var ttl: Seconds = 0.5f) : Component
class TargetSquare(val target: Mat4) : Component
class SquareHit(val start: Mat4, val target: Mat4, val duration: Seconds = 0.5f, var ttl: Seconds = duration) :
    Component

class Blend(var percent: Float) : Component

class CreateCircle(var position: ImmutableVector3) : Event
class CreateSquare(var position: ImmutableVector3) : Event

object HitCircle : Event
object HitSquare : Event
class StopGame(score: Int) : Event

class InterpolationSystem : System(EntityQuery.of(Blend::class)) {

    val squares by interested(EntityQuery.of(Square::class))

    override fun update(delta: Seconds, entity: Entity) {
        val (a, b) = squares

        val blend = entity.get(Blend::class).percent
        val interpo = Interpolations.interpolate(b.position.localTransformation, a.position.localTransformation, blend)
        // entity.position.setLocalScale(interpo.scale.x, interpo.scale.y, interpo.scale.z)
        // entity.position.setLocalRotation(interpo.rotation.x, interpo.rotation.y, interpo.rotation.z)
        // entity.position.setLocalTranslation(interpo.translation.x, interpo.translation.y, interpo.translation.z)
        entity.position.setLocalTransform(interpo)
        if (input.isKeyPressed(Key.ARROW_RIGHT)) {
            entity.get(Blend::class).percent += 0.3f * delta
        } else if (input.isKeyPressed(Key.ARROW_LEFT)) {
            entity.get(Blend::class).percent -= 0.3f * delta
        }

        entity.get(Blend::class).percent = max(0.0f, min(entity.get(Blend::class).percent, 1.0f))
    }
}

class DebugSystem : ImGuiSystem() {

    val blend by interested(EntityQuery.of(Blend::class))

    override fun gui(builder: WidgetBuilder<Texture>) {
        builder.verticalContainer {
            blend.forEach {
                button(label = "Blend: " + (it.get(Blend::class).percent * 100).roundToInt())
            }
        }
    }
}

class ScoreSystem : System(EntityQuery.of(Score::class)) {

    var score = 0

    override fun update(delta: Seconds, entity: Entity) {
        (entity.get(TextComponent::class).text as WriteText).content = score.toString()
    }

    override fun onEvent(event: Event, entityQuery: EntityQuery?) {
        if (event is HitSquare) {
            emit(StopGame(score))
            score = 0
        } else if (event is HitCircle) {
            score += 10
        }
    }
}

class HitSystem : System(EntityQuery.of(Hit::class)) {

    val players by interested(EntityQuery.of(Player::class))
    val circles by interested(EntityQuery.of(Circle::class))
    val square by interested(EntityQuery.of(Square::class))

    val targetSquare by interested(EntityQuery.of(TargetSquare::class))

    private val collider = AABBCollisionResolver()

    override fun update(delta: Seconds, entity: Entity) {

        if (collider.collide(entity, players.first())) {
            val target =
                circles.filter { !it.get(Circle::class).touched }.firstOrNull { collider.collide(entity, it) }
            if (target != null) {
                target.get(Circle::class).touched = true
                target.add(Disappear())
                target.get(Movable::class).speed = -2f
                emit(HitCircle)
            } else {
                val target =
                    square.filter { !it.get(Square::class).touched }.firstOrNull { collider.collide(entity, it) }
                if (target != null) {
                    target.get(Square::class).touched = true
                    target.remove(Movable::class)
                    target.add(
                        SquareHit(
                            target.position.localTransformation,
                            targetSquare.first().get(TargetSquare::class).target
                        )
                    )
                    emit(HitSquare)
                }
            }
        }
    }
}

class SquareHitSystem : System(EntityQuery.of(SquareHit::class)) {

    override fun update(delta: Seconds, entity: Entity) {
        val squareHit = entity.get(SquareHit::class)
        squareHit.ttl -= delta
        val blend = min(1f - squareHit.ttl / squareHit.duration, 1f)
        val interpolate = Interpolations.interpolate(
            target = squareHit.target,
            start = squareHit.start,
            blend = blend
        )
        entity.position.setLocalTransform(interpolate)
    }
}

class GeneratorSystem : TemporalSystem(0.3f, query = EntityQuery.none()) {

    val generators by interested(EntityQuery.of(Generator::class))

    override fun update(delta: Seconds, entity: Entity) = Unit

    override fun timeElapsed() {
        val generator = generators.random()
        val randomValue = Random.nextFloat()

        if (randomValue < 0.5f) {
            emit(CreateCircle(generator.position.translation))
        } else if (randomValue < 0.75) {
            emit(CreateSquare(generator.position.translation))
        }
    }
}

class DisappearSystem : System(EntityQuery.Companion.of(Disappear::class)) {

    override fun update(delta: Seconds, entity: Entity) {
        val disappear = entity.get(Disappear::class)
        disappear.ttl -= delta
        if (disappear.ttl < 0f) {
            entity.destroy()
            return
        }

        entity.position.setLocalRotation(
            y = Interpolations.lerp(90f, entity.position.localRotation.y)
        )
    }
}

class MovableSystem : System(EntityQuery.of(Movable::class)) {

    val destroyers by interested(EntityQuery.of(Destroy::class))

    var limit: Float = 0f

    override fun onGameStarted(engine: Engine) {
        limit = destroyers.first().position.translation.y
    }

    override fun update(delta: Seconds, entity: Entity) {
        entity.position.addLocalTranslation(y = entity.get(Movable::class).speed, delta = delta)

        // Out of the screen
        if (entity.position.translation.y <= limit) {
            entity.destroy()
        }
    }

    override fun onEvent(event: Event, entityQuery: EntityQuery?) {
        if (event is CreateCircle) {
            val entity = entityFactory.createFromTemplate("circle")
            entity.position.setLocalTranslation(
                event.position.x,
                event.position.y,
                event.position.z
            )
        } else if (event is CreateSquare) {
            val entity = entityFactory.createFromTemplate("square")
            entity.position.setLocalTranslation(
                event.position.x,
                event.position.y,
                event.position.z
            )
        }
    }
}

class PlayerSystem : System(EntityQuery.of(Player::class)) {

    lateinit var left: Entity
    lateinit var right: Entity

    val hits by interested(EntityQuery.of(Hit::class))

    lateinit var current: Entity

    override fun onGameStarted(engine: Engine) {
        val (a, b) = hits
        if (a.name == "hit2") {
            left = a
            right = b
        } else {
            right = a
            left = b
        }

        current = left
    }

    override fun update(delta: Seconds, entity: Entity) {
        if (input.isKeyJustPressed(Key.ARROW_LEFT)) {
            current = left
        } else if (input.isKeyJustPressed(Key.ARROW_RIGHT)) {
            current = right
        }
        entity.position.setLocalTranslation(
            x = Interpolations.lerp(
                current.position.translation.x,
                entity.position.translation.x,
                step = 0.8f
            )
        )
    }
}

class MyGame(override val gameContext: GameContext) : Game {

    private val scene by gameContext.fileHandler.get<GraphScene>("circles.protobuf")
    private val font by gameContext.fileHandler.get<Font>("font")

    override fun createEntities(entityFactory: EntityFactory) {
        // Create all entities needed at startup
        // The scene is the node graph that can be updated in Blender
        scene.nodes.forEach { node ->
            // Create an entity using all information from this node (model, position, camera, ...)
            if (node.name.startsWith("hit")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Hit())
            } else if (node.name.startsWith("player")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Player())
            } else if (node.name.startsWith("destroy")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Destroy())
            } else if (node.name.startsWith("circle")) {
                entityFactory.registerTemplate("circle") {
                    val entity = entityFactory.createFromNode(node)
                    entity.add(Circle())
                    entity.add(Movable())
                    entity
                }
            } else if (node.name.startsWith("generator")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Generator())
            } else if (node.name.startsWith("square")) {
                entityFactory.registerTemplate("square") {
                    val entity = entityFactory.createFromNode(node)
                    entity.add(Square())
                    entity.add(Movable())
                    entity
                }
            }else if(node.name.startsWith("targetSquare")) {
                entityFactory.create {
                    val transformation = node.combinedTransformation
                    add(TargetSquare(transformation))
                }
            } else if (node.name.startsWith("score")) {
                val entity = entityFactory.createText("00000", font, node)
                entity.get(TextComponent::class).horizontalAlign = HorizontalAlignment.Center
                entity.add(Score())
            } else {
                entityFactory.createFromNode(node)
            }
        }
    }

    override fun createSystems(engine: Engine): List<System> {
        // Create all systems used by the game
        return listOf(
            PlayerSystem(),
            MovableSystem(),
        //
            //    InterpolationSystem(),
            GeneratorSystem(),
            HitSystem(),
            ScoreSystem(),
            DisappearSystem(),
            SquareHitSystem(),
            DebugSystem()
        )
    }
}
