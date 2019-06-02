package fi.dy.masa.tweakeroo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.tweakeroo.config.Configs;
import fi.dy.masa.tweakeroo.config.FeatureToggle;
import fi.dy.masa.tweakeroo.util.CameraEntity;
import fi.dy.masa.tweakeroo.util.MiscUtils;
import fi.dy.masa.tweakeroo.util.SnapAimMode;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

@Mixin(Entity.class)
public abstract class MixinEntity
{
    @Shadow
    public World world;

    @Shadow public float rotationPitch;
    @Shadow public float rotationYaw;
    @Shadow public float prevRotationPitch;
    @Shadow public float prevRotationYaw;
    @Shadow public double motionX;
    @Shadow public double motionY;
    @Shadow public double motionZ;

    private double realPitch;
    private double realYaw;
    private double realPitch2;
    private double realYaw2;

    @Redirect(method = "move",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;onGround:Z", ordinal = 0)),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z", ordinal = 0))
    private boolean fakeSneaking(Entity entity)
    {
        if (FeatureToggle.TWEAK_FAKE_SNEAKING.getBooleanValue() && ((Object) this) instanceof EntityPlayerSP)
        {
            return true;
        }

        return ((Entity) (Object) this).isSneaking();
    }

    @Inject(method = "moveRelative",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/util/math/MathHelper;sin(F)F"), cancellable = true)
    private void moreAccurateMoveRelative(float strafe, float up, float forward, float friction, CallbackInfo ci)
    {
        if ((Object) this instanceof EntityPlayerSP)
        {
            if (FeatureToggle.TWEAK_FREE_CAMERA.getBooleanValue() && FeatureToggle.TWEAK_FREE_CAMERA_MOTION.getBooleanValue())
            {
                CameraEntity camera = CameraEntity.getCamera();

                if (camera != null)
                {
                    this.motionY = 0;
                    ci.cancel();
                }
            }
            else if (FeatureToggle.TWEAK_SNAP_AIM.getBooleanValue())
            {
                double xFactor = Math.sin(this.rotationYaw * Math.PI / 180D);
                double zFactor = Math.cos(this.rotationYaw * Math.PI / 180D);

                this.motionX += (double) (strafe * zFactor - forward * xFactor);
                this.motionY += (double) up;
                this.motionZ += (double) (forward * zFactor + strafe * xFactor);

                ci.cancel();
            }
        }
    }

    @Inject(method = "rotateTowards", at = @At("HEAD"), cancellable = true)
    private void overrideYaw(double yawChange, double pitchChange, CallbackInfo ci)
    {
        if ((Object) this instanceof EntityPlayerSP)
        {
            if (FeatureToggle.TWEAK_FREE_CAMERA.getBooleanValue() && FeatureToggle.TWEAK_FREE_CAMERA_MOTION.getBooleanValue())
            {
                this.realYaw2 += (double) yawChange * 0.15D;
                this.realPitch2 = MathHelper.clamp(this.realPitch2 + (double) pitchChange * 0.15D, -90, 90);

                CameraEntity camera = CameraEntity.getCamera();

                if (camera != null)
                {
                    camera.setRotations((float) this.realYaw2, (float) this.realPitch2);
                }
            }
            else
            {
                this.realYaw2 = this.rotationYaw;
                this.realPitch2 = this.rotationPitch;
            }

            if (FeatureToggle.TWEAK_AIM_LOCK.getBooleanValue() ||
                (FeatureToggle.TWEAK_FREE_CAMERA.getBooleanValue() && FeatureToggle.TWEAK_FREE_CAMERA_MOTION.getBooleanValue()))
            {
                this.rotationYaw = (float) this.realYaw;
                this.rotationPitch = (float) this.realPitch;
                this.prevRotationYaw = this.rotationYaw;
                this.prevRotationPitch = this.rotationPitch;
                ci.cancel();
            }
            else
            {
                if (FeatureToggle.TWEAK_SNAP_AIM.getBooleanValue())
                {
                    int limit = Configs.Generic.SNAP_AIM_PITCH_OVERSHOOT.getBooleanValue() ? 180 : 90;
                    SnapAimMode mode = (SnapAimMode) Configs.Generic.SNAP_AIM_MODE.getOptionListValue();
                    boolean snapAimLock = FeatureToggle.TWEAK_SNAP_AIM_LOCK.getBooleanValue();

                    // Not locked, or not snapping the yaw (ie. not in Yaw or Both modes)
                    if (snapAimLock == false || mode == SnapAimMode.PITCH)
                    {
                        this.realYaw += (double) yawChange * 0.15D;
                    }

                    if (snapAimLock == false || mode == SnapAimMode.YAW)
                    {
                        this.realPitch = MathHelper.clamp(this.realPitch + (double) pitchChange * 0.15D, -limit, limit);
                    }

                    this.rotationYaw = MiscUtils.getSnappedYaw(this.realYaw);
                    this.rotationPitch = MiscUtils.getSnappedPitch(this.realPitch);
                    this.prevRotationYaw = this.rotationYaw;
                    this.prevRotationPitch = this.rotationPitch;
                    ci.cancel();
                }
                // Update the internal rotations while Aim Lock is not active.
                // They will then be used as the forced rotations if the Aim Lock is activated.
                else
                {
                    this.realYaw = this.rotationYaw;
                    this.realPitch = this.rotationPitch;
                }
            }
        }
    }
}
