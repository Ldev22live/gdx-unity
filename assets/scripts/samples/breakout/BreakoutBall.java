package samples.breakout;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The ball and the rules of Breakout: launch with Space, bounce off walls, the paddle and bricks, lose a life when
 * the ball drops below the paddle. Bricks are the entities whose name starts with "Brick"; a hit brick is
 * deactivated, so R (restart) can bring every brick back.
 */
public class BreakoutBall extends ScriptComponent {
    /** Launch speed in units per second. */
    static final float START_SPEED = 360f;
    /** Speed gained per brick hit. */
    static final float SPEED_GAIN = 6f;
    /** Upper limit, so the ball stays catchable. */
    static final float MAX_SPEED = 720f;
    /** Steepest bounce off the paddle edge, in degrees from vertical. */
    static final float MAX_BOUNCE_ANGLE = 60f;
    /** Inner edges of the walls. */
    static final float LEFT = 10f;
    static final float RIGHT = 630f;
    static final float TOP = 470f;
    /** Points per brick. */
    static final int BRICK_POINTS = 10;
    /** Lives at the start of a game. */
    static final int START_LIVES = 3;

    private final List<Entity> bricks = new ArrayList<>();
    private Entity paddle;
    private float paddleHalfWidth;
    private float paddleHalfHeight;
    private float radius;
    private TextComponent scoreText;
    private TextComponent livesText;
    private TextComponent messageText;

    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private float speed;
    private boolean launched;
    private boolean gameOver;
    private int score;
    private int lives;

    @Override
    public void onStart() {
        super.onStart();
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Brick")) {
                bricks.add(entity);
            }
        }
        paddle = getScene().findEntityByName("Paddle");
        ShapeRendererComponent paddleShape = paddle.getComponent(ShapeRendererComponent.class);
        paddleHalfWidth = paddleShape.getWidth() / 2f;
        paddleHalfHeight = paddleShape.getHeight() / 2f;
        radius = getComponent(ShapeRendererComponent.class).getWidth() / 2f;
        scoreText = getScene().findEntityByName("Score Text").getComponent(TextComponent.class);
        livesText = getScene().findEntityByName("Lives Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
        restart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        if (gameOver) {
            return;
        }
        if (!launched) {
            Vector3 paddlePosition = paddle.getTransform().getWorldPosition();
            x = paddlePosition.x;
            y = paddlePosition.y + paddleHalfHeight + radius + 1f;
            if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
                launched = true;
                AudioSourceComponent.play(getScene(), "launch");
                messageText.setText("");
                velocityX = speed * 0.4f;
                velocityY = speed;
                normalizeVelocity();
            }
        } else {
            step(deltaTime);
        }
        getTransform().setLocalPosition(x, y, 0f);
    }

    private void step(float deltaTime) {
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;
        if (x - radius < LEFT) { x = LEFT + radius; velocityX = Math.abs(velocityX); }
        if (x + radius > RIGHT) { x = RIGHT - radius; velocityX = -Math.abs(velocityX); }
        if (y + radius > TOP) { y = TOP - radius; velocityY = -Math.abs(velocityY); }

        Vector3 paddlePosition = paddle.getTransform().getWorldPosition();
        if (velocityY < 0 && overlaps(paddlePosition.x, paddlePosition.y, paddleHalfWidth, paddleHalfHeight)) {
            // Where the ball hits the paddle sets the bounce angle: centre straight up, edges steep
            float hit = MathUtils.clamp((x - paddlePosition.x) / paddleHalfWidth, -1f, 1f);
            float angle = hit * MAX_BOUNCE_ANGLE * MathUtils.degreesToRadians;
            velocityX = speed * MathUtils.sin(angle);
            velocityY = speed * MathUtils.cos(angle);
            y = paddlePosition.y + paddleHalfHeight + radius;
            AudioSourceComponent.play(getScene(), "paddle");
        }

        hitBricks();
        if (y < -radius) {
            loseLife();
        }
    }

    /** Bounces off the first brick touched, on the side with the smaller overlap. */
    private void hitBricks() {
        for (Entity brick : bricks) {
            if (!brick.isActiveSelf()) {
                continue;
            }
            ShapeRendererComponent shape = brick.getComponent(ShapeRendererComponent.class);
            Vector3 position = brick.getTransform().getWorldPosition();
            float halfWidth = shape.getWidth() / 2f;
            float halfHeight = shape.getHeight() / 2f;
            if (!overlaps(position.x, position.y, halfWidth, halfHeight)) {
                continue;
            }
            float overlapX = halfWidth + radius - Math.abs(x - position.x);
            float overlapY = halfHeight + radius - Math.abs(y - position.y);
            if (overlapX < overlapY) {
                velocityX = x < position.x ? -Math.abs(velocityX) : Math.abs(velocityX);
            } else {
                velocityY = y < position.y ? -Math.abs(velocityY) : Math.abs(velocityY);
            }
            brick.setActive(false);
            score += BRICK_POINTS;
            AudioSourceComponent.play(getScene(), "brick");
            speed = Math.min(MAX_SPEED, speed + SPEED_GAIN);
            normalizeVelocity();
            updateTexts();
            if (bricksLeft() == 0) {
                gameOver = true;
                AudioSourceComponent.play(getScene(), "win");
                messageText.setText("You cleared the wall! Score " + score + ". Press R to play again");
            }
            return;
        }
    }

    private void loseLife() {
        lives--;
        AudioSourceComponent.play(getScene(), "lose");
        launched = false;
        updateTexts();
        if (lives <= 0) {
            gameOver = true;
            messageText.setText("Game over. Score " + score + ". Press R to play again");
        } else {
            messageText.setText("Press Space to launch");
        }
    }

    private void restart() {
        for (Entity brick : bricks) {
            brick.setActive(true);
        }
        score = 0;
        lives = START_LIVES;
        speed = START_SPEED;
        launched = false;
        gameOver = false;
        messageText.setText("Press Space to launch");
        updateTexts();
    }

    private boolean overlaps(float boxX, float boxY, float halfWidth, float halfHeight) {
        return Math.abs(x - boxX) < halfWidth + radius && Math.abs(y - boxY) < halfHeight + radius;
    }

    private void normalizeVelocity() {
        float length = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        velocityX = velocityX / length * speed;
        velocityY = velocityY / length * speed;
    }

    private int bricksLeft() {
        int left = 0;
        for (Entity brick : bricks) {
            if (brick.isActiveSelf()) {
                left++;
            }
        }
        return left;
    }

    private void updateTexts() {
        scoreText.setText("Score: " + score);
        livesText.setText("Lives: " + lives);
    }
}
