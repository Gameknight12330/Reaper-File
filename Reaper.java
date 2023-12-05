package com.projectmushroom.lavapotions.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.projectmushroom.lavapotions.effect.LavaEffects;
import java.lang.Integer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PowerableMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Reaper extends Monster implements PowerableMob {
	   private static final EntityDataAccessor<Integer> DATA_ID_ATTACK_TARGET = SynchedEntityData.defineId(Reaper.class, EntityDataSerializers.INT);
	   private static final EntityDataAccessor<Integer> DATA_ID_INV = SynchedEntityData.defineId(Reaper.class, EntityDataSerializers.INT);
	   private static final int INVULNERABLE_TICKS = 220;
	   private int rangedDelay = 80;
	   private int burstDelay = 4;
	   private int shotsFired = 0;
	   private boolean isBursting;
	   private int destroyBlocksTick;
	   private boolean isSlamming;
	   private boolean startedSlam;
	   private int postSlamCooldown = 0;
	   private int swipeCooldown = 0;
	   private int timeSpun = 0;
	   private boolean foundEndSpot = false;
	   private boolean isSpinning = false;
	   private int timeStuck = 0;
	   private int spinCooldown = 0;
	   private int delayedWave = 2;
	   private boolean performingWave = false;
	   private BlockPos waveStart;
	   private Explosion waveexplosion;
	   private int swipeNum = 0;
	   private boolean isMultiSwiping = false;
	   private int multiSwipeCooldown = 0;
	   private boolean isBetweenSwipes = true;
	   private int timeBetweenSwipes = 14;
	   @Nullable
	   private LivingEntity clientSideCachedAttackTarget;
	   private int clientSideAttackTime;
	   protected final int ATTACK_TIME = 20;
	   private boolean playerBlockedBeam;
	   private final ServerBossEvent bossEvent = (ServerBossEvent)(new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS)).setDarkenScreen(true);
	   private static final Predicate<LivingEntity> LIVING_ENTITY_SELECTOR = (p_31504_) -> {
	      return p_31504_.getMobType() != MobType.UNDEAD && p_31504_.attackable();
	   };
	   private static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forCombat().range(20.0D).selector(LIVING_ENTITY_SELECTOR);

	   public Reaper(EntityType<? extends Reaper> p_31437_, Level p_31438_) {
	      super(p_31437_, p_31438_);
	      moveControl = new FlyingMoveControl(this, 10, false);
	      setHealth(500);
	      xpReward = 300;
	   }

	   protected PathNavigation createNavigation(Level p_186262_) {
	      FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, p_186262_);
	      flyingpathnavigation.setCanOpenDoors(true);
	      flyingpathnavigation.setCanFloat(true);
	      flyingpathnavigation.setCanPassDoors(true);
	      return flyingpathnavigation;
	   }

	   protected void registerGoals() {
	      goalSelector.addGoal(0, new Reaper.ReaperDoNothingGoal());
	      goalSelector.addGoal(4, new Reaper.ReaperLaserGoal(this));
	      goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
	      goalSelector.addGoal(6, new Reaper.ReaperLookAtPlayerGoal(this, Player.class, 8.0F));
	      targetSelector.addGoal(1, new HurtByTargetGoal(this));
	      targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 0, false, false, LIVING_ENTITY_SELECTOR));
	   }

	   protected void defineSynchedData() {
	      super.defineSynchedData();
	      entityData.define(DATA_ID_ATTACK_TARGET, 0);
	      entityData.define(DATA_ID_INV, 0);
	   }

	   public void addAdditionalSaveData(CompoundTag p_31485_) {
	      super.addAdditionalSaveData(p_31485_);
	      p_31485_.putInt("Invul", getInvulnerableTicks());
	   }

	   public void readAdditionalSaveData(CompoundTag p_31474_) {
	      super.readAdditionalSaveData(p_31474_);
	      setInvulnerableTicks(p_31474_.getInt("Invul"));
	      if (hasCustomName()) {
	         bossEvent.setName(getDisplayName());
	      }

	   }

	   public void setCustomName(@Nullable Component p_31476_) {
	      super.setCustomName(p_31476_);
	      bossEvent.setName(getDisplayName());
	   }

	   protected SoundEvent getAmbientSound() {
	      return SoundEvents.WITHER_AMBIENT;
	   }

	   protected SoundEvent getHurtSound(DamageSource p_31500_) {
	      return SoundEvents.WITHER_HURT;
	   }

	   protected SoundEvent getDeathSound() {
	      return SoundEvents.WITHER_DEATH;
	   }

	   public void aiStep() {
	      Vec3 vec3 = getDeltaMovement().multiply(1.0D, 0.6D, 1.0D);
	      if (!level.isClientSide && hasActiveAttackTarget()) {
	         Entity entity = getActiveAttackTarget();
	         if (entity != null) {
	            double d0 = vec3.y;
	            if (getY() < entity.getY() || (!isPowered() && !isSpinning && !isMultiSwiping && getY() < entity.getY() + 3.5D)) {
	               d0 = Math.max(0.0D, d0);
	               d0 += 0.3D - d0 * (double)0.6F;
	            }
	            if (((isSpinning && !foundEndSpot) || isMultiSwiping) && getEyeY() - 5 > entity.getY())
	            {
	            	d0 = Math.max(0.0D, d0);
		            d0 -= 0.6D - d0 * (double)1.2F;
	            }
	            vec3 = new Vec3(vec3.x, d0, vec3.z);
	            Vec3 vec31 = new Vec3(entity.getX() - getX(), 0.0D, entity.getZ() - getZ());
	            if (vec31.horizontalDistanceSqr() > 100.0D && !isSpinning) {
	               Vec3 vec32 = vec31.normalize();
	               vec3 = vec3.add(vec32.x * 0.3D - vec3.x * 0.6D, 0.0D, vec32.z * 0.3D - vec3.z * 0.6D);
	            }
	            if (isSpinning && !foundEndSpot && vec31.horizontalDistanceSqr() > 0) {
		           Vec3 vec32 = vec31.normalize();
		           vec3 = vec3.add(vec32.x * 0.03D - vec3.x * 0.06D, 0.0D, vec32.z * 0.03D - vec3.z * 0.06D);
	            }
	            if (isMultiSwiping && isBetweenSwipes && vec31.horizontalDistanceSqr() > 4) {
			       Vec3 vec32 = vec31.normalize();
			       vec3 = vec3.add(vec32.x * 0.6D - vec3.x * 1.2D, 0.0D, vec32.z * 0.6D - vec3.z * 1.2D);
		        }
	         }
	      }

	      setDeltaMovement(vec3);
	      if (vec3.horizontalDistanceSqr() > 0.05D && !isSpinning) {
	         setYRot((float)Mth.atan2(vec3.z, vec3.x) * (180F / (float)Math.PI) - 90.0F);
	      }

	      super.aiStep();

	   }

	   protected void customServerAiStep() {
	      if (getInvulnerableTicks() > 0) {
	         int k1 = getInvulnerableTicks() - 1;
	         bossEvent.setProgress(1.0F - (float)k1 / 220.0F);
	         if (k1 <= 0) {
	            Explosion.BlockInteraction explosion$blockinteraction = net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level, this) ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.NONE;
	            level.explode(this, getX(), getEyeY(), getZ(), 7.0F, false, explosion$blockinteraction);
	            if (!isSilent()) {
	               level.globalLevelEvent(1023, blockPosition(), 0);
	            }
	         }

	         setInvulnerableTicks(k1);

	      } else {
	         super.customServerAiStep();
	         Explosion clearPath = new Explosion(level, this, new DamageSource("dont"), null, getX(), getY(), getZ(), 3, false, Explosion.BlockInteraction.NONE);
	         clearPath.explode();
	         List<BlockPos> marked = clearPath.getToBlow();
	         for (BlockPos pos : marked)
	         {
	        	 int howFarDown = 4;
	        	 if (hasActiveAttackTarget())
	        	 {
	        		 if (getActiveAttackTarget().getY() < getEyeY() - 4)
	        		 {
	        			 howFarDown = 5;
	        		 }
	        	 }
	        	 if (pos.getY() >= getEyeY() - howFarDown)
	        	 {
	        		 level.destroyBlock(pos, true);
	        	 }
	         }
	         clearPath.finalizeExplosion(false);
	         
	         if (tickCount >= 800 && tickCount % 800 == 0 && !isPowered())
	         {
	        	 isSlamming = true;
	         }
	         
	         if ((tickCount - 49) % 50 != 0 && isSlamming && !startedSlam && postSlamCooldown == 0)
	         {
	        	 moveTo(getBlockX(), getBlockY() + 1, getBlockZ());
	         }
	         else if (isSlamming)
	         {
	        	 if (postSlamCooldown == 0)
	        	 {
	        		 startedSlam = true;
	        	 }
	        	 
	        	 if (level.getBlockState(new BlockPos(getX(), getEyeY() - 5, getZ())).getBlock() == Blocks.AIR && startedSlam && postSlamCooldown == 0)
	        	 {
	        		 moveTo(getBlockX(), getBlockY() - 1, getBlockZ());
	        	 }
	        	 else if (startedSlam)
	        	 {
	        		 if (!level.isClientSide) 
	        		 {
	        			 Explosion explosion = new Explosion(level, this, new DamageSource("reaper"), null, getX(), getY(), getZ(), 5, false, Explosion.BlockInteraction.BREAK);
	        			 explosion.explode();
	        			 List<BlockPos> marked2 = explosion.getToBlow();
	        		     for (BlockPos pos : marked2) 
	        		     {
	        		    	 BlockState state = level.getBlockState(pos);
	        		    	 if (state.getBlock() != Blocks.AIR)
	        		    	 {
	        		             CustomFallingBlockEntity sand = new CustomFallingBlockEntity(level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), state);
	        		             sand.setDeltaMovement(Math.random() - Math.random(), 0.5D + Math.random() * 2.0D, Math.random() - Math.random());
	        		             level.addFreshEntity(sand);
	        		    	 }
	        		     } 
	        		     explosion.finalizeExplosion(false);
	        		     startedSlam = false;
	        		     postSlamCooldown += 1;
	        		 } 
	        	 } 
	        	 else
	             {
	        		 postSlamCooldown += 1;
	        		 if (postSlamCooldown >= 120)
	        		 {
	        			 isSlamming = false;
	        			 postSlamCooldown = 0;
	        		 }
	        		 else
	        		 {
	        			 setDeltaMovement(0, 0, 0);
	        		 }
	             }
	         }
	         
	         if ((tickCount % rangedDelay == 0 || isBursting) && !isSlamming && hasActiveAttackTarget() && !isSpinning) 
	         {
	        	isBursting = true;
	            if ((level.getDifficulty() == Difficulty.NORMAL || level.getDifficulty() == Difficulty.HARD) 
	            	&& isBursting && tickCount % burstDelay == 0 && shotsFired < 4)  
	            {
	            	shotsFired += 1;
	            	if (shotsFired == 4 && !isPowered())
	            	{
	            		performRangedAttack(0, getActiveAttackTarget(), true);
	            	}
	            	else if (shotsFired == 4 && isPowered())
	            	{
	            		performRangedAttack(1, getActiveAttackTarget(), true);
	            	}
	            	else
	            	{
	            		performRangedAttack(0, getActiveAttackTarget(), false);
	            	}
	            }
	            else if (shotsFired >= 4)
	            {
	            	isBursting = false;
	            	shotsFired = 0;
	            }
	         }
	         if (hasActiveAttackTarget()) {
	        	if (isPowered())
	        	{
	        		spinCooldown += 1;
	        	    multiSwipeCooldown += 1;
	        	}
	        	
	        	swipeCooldown += 1;
	            LivingEntity livingentity = (LivingEntity)getActiveAttackTarget();
	            if (livingentity != null && canAttack(livingentity) && playerBlockedBeam && !isSlamming && !isSpinning)
	            {
	               lookAt(livingentity, livingentity.getYRot(), livingentity.getXRot());
	               int addx = 1;
	               int addz = 1;
	               if (getX() < livingentity.getX())
	               {
	                  addx = -1;
	               }
	               if (getZ() < livingentity.getZ())
	               {
	                  addz = -1;
	               }
	               moveTo(livingentity.getX() + addx, livingentity.getY() - 2, livingentity.getZ() + addz);
	            }
	            if (livingentity != null && canAttack(livingentity) && !(distanceToSqr(livingentity) > 36.0D || !hasLineOfSight(livingentity) || swipeCooldown <= 40 || isSlamming || isSpinning || isMultiSwiping))
	            {
	               swipeCooldown = 0;
	               sweepingAttack(livingentity);
	               playerBlockedBeam = false;
	            }
	            if (livingentity != null && canAttack(livingentity) && spinCooldown >= 500 && !isMultiSwiping && isPowered())
	            {
	            	isSpinning = true;
	            	spinningAttack(livingentity);
	            }
	            if (livingentity != null && canAttack(livingentity) && multiSwipeCooldown >= 600 && !isSpinning && isPowered())
	            {
	            	isMultiSwiping = true;
	            	multiSwipe(livingentity);
	            }
	         } 
	         else 
	         {
	            List<LivingEntity> list = level.getNearbyEntities(LivingEntity.class, TARGETING_CONDITIONS, this, getBoundingBox().inflate(20.0D, 8.0D, 20.0D));
	            if (!list.isEmpty()) 
	            {
	                LivingEntity livingentity1 = list.get(random.nextInt(list.size()));
	                setActiveAttackTarget(livingentity1.getId());
	            }
	         }
	      }

	      if (getTarget() != null) 
	      {
	         setActiveAttackTarget(getTarget().getId());
	      } 
	      else 
	      {
	         setActiveAttackTarget(0);
	      }

	      if (tickCount % 20 == 0) {
	        	if (isPowered())
	        	{
	        		heal(2.0F);
	        	}
	            heal(0.5F);
	         }

	      bossEvent.setProgress(getHealth() / getMaxHealth());
	   }

	   @Deprecated //Forge: DO NOT USE use BlockState.canEntityDestroy
	   public static boolean canDestroy(BlockState p_31492_) {
	      return !p_31492_.isAir() && !p_31492_.is(BlockTags.WITHER_IMMUNE);
	   }

	   public void makeInvulnerable() {
	      setInvulnerableTicks(220);
	      bossEvent.setProgress(0.0F);
	      setHealth(getMaxHealth() / 3.0F);
	   }

	   public void makeStuckInBlock(BlockState p_31471_, Vec3 p_31472_) {
	   }
	   
	   public boolean requiresUpdateEveryTick() {
	      return true;
	   }

	   public void startSeenByPlayer(ServerPlayer p_31483_) {
	      super.startSeenByPlayer(p_31483_);
	      bossEvent.addPlayer(p_31483_);
	   }
	   
	   public float getAttackAnimationScale(float p_32813_) {
		  return ((float)clientSideAttackTime + p_32813_) / (float)getAttackDuration();
	   }
	   
	   public int getAttackDuration() {
		  return ATTACK_TIME;
	   }

	   public void stopSeenByPlayer(ServerPlayer p_31488_) {
	      super.stopSeenByPlayer(p_31488_);
	      bossEvent.removePlayer(p_31488_);
	   }
	   
	   private void sweepingAttack(LivingEntity target)
	   {
		   lookAt(target, 360, 360);
		   List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.0D, 1.0D, 6.0D));
		   List<LivingEntity> hitEntities = new ArrayList<LivingEntity>();
		   for (int e = 0; e < list.size(); e++)
		   {
			   if (hasLineOfSight(list.get(e)) && !(list.get(e) instanceof Reaper)) 
			   {
                   hitEntities.add(list.get(e));
               }
		   }
		   ArrayList<Double> adds = doComplexMath(5);
		   double addx = adds.get(0);
		   double addz = adds.get(1);
		   for (int h = 0; h < hitEntities.size(); h++)
		   {
			   boolean noDamage = false;
			   if (hitEntities.get(h) instanceof Player)
			   {
				   Player player = (Player) hitEntities.get(h);
				   if (player.isBlocking())
				   {
				       player.disableShield(true);
				       noDamage = true;
				   }
			   }
			   hitEntities.get(h).setDeltaMovement(addx, 2, addz);
			   if (!noDamage)
			   {
			       hitEntities.get(h).hurt(new DamageSource("Reaper"), 20);
			   }
		   }
	   }
	   
	   private void spinningAttack(LivingEntity target)
	   {
		   timeSpun += 1;
		   System.out.println("Beyblade Spinning");
		   if (timeSpun < 100)
		   {
			   spinningDamage();
			   System.out.println(getYRot());
		   }
		   else if (timeSpun < 200 && !foundEndSpot)
		   {
			   float returnrot = getYRot();
			   float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90;
			   this.setYRot(yaw);
			   ArrayList<Double> adds = doComplexMath(5);
			   double blockx = adds.get(0);
			   double blockz = adds.get(1);
			   Block landingblock = level.getBlockState(new BlockPos(getX() + blockx, getEyeY() - 5, getZ() + blockz)).getBlock();
			   System.out.println("Checking if block at x = " + (getX() + blockx) + ", y = " + (getEyeY() - 5) + ", z = " + (getZ() + blockz) + " is air");
			   if (landingblock != Blocks.AIR)
			   {
				   System.out.println("The block is real");
				   waveStart = new BlockPos(getX() + blockx, getEyeY() - 5, getZ() + blockz);
				   performingWave = true;
      		       foundEndSpot = true;
			   }
			   else
			   {
				   setYRot(returnrot);
				   spinningDamage();
			   }
		   }
		   else if (performingWave)
		   {
			   if (delayedWave == 2)
			   {
				   waveexplosion = new Explosion(level, this, new DamageSource("reaper"), null, waveStart.getX(), waveStart.getY(), waveStart.getZ(), 7, false, Explosion.BlockInteraction.NONE);
				   waveexplosion.explode();
			   }
			   delayedWave += 1;
  			   List<BlockPos> marked = waveexplosion.getToBlow();
  			   for (BlockPos pos : marked) 
  			   {
  				   BlockState state = level.getBlockState(pos);
  				   if (delayedWave % 3 == 0)
    		       {
    		    	  if ((Math.abs(pos.getX() - waveStart.getX()) == (delayedWave / 3) || Math.abs(pos.getZ() - waveStart.getZ()) == (delayedWave / 3)) && (Math.abs(pos.getY() - waveStart.getY()) <= 1))
    		    	  {
      		    	     if (state.getBlock() != Blocks.AIR && level.getBlockState(pos.above()).getBlock() == Blocks.AIR)
      		    	     {
      		                CustomFallingBlockEntity sand = new CustomFallingBlockEntity(level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), state);
      		                sand.setDeltaMovement(0, 0.5D, 0);
      		                level.removeBlock(pos, false);
      		                level.addFreshEntity(sand);
      		    	     }
    		    	  }
    		       }
  			   }
  			   if (delayedWave >= 21)
		       {
		    	   waveexplosion = null;
		    	   performingWave = false;
		       }
		   }
		   else if (timeStuck < 150 && foundEndSpot)
		   {
			   timeStuck += 1;
		   }
		   else
		   {
			   timeStuck = 0;
			   delayedWave = 2;
			   timeSpun = 0;
			   spinCooldown = 0;
			   foundEndSpot = false;
			   isSpinning = false;
		   }
	   }
	   
	   private void spinningDamage()
	   {
		   setYRot(getYRot() - 50);
		   if (getYRot() < 0)
		   {
			   setYRot(360 + getYRot());
		   }
		   List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.5D, 1.0D, 6.5D));
		   ArrayList<Double> adds = doComplexMath(20);
		   double addx = adds.get(0);
		   double addz = adds.get(1);
		   for (LivingEntity dead : entities)
		   {
			   if (!(dead == this))
			   {
				   dead.setDeltaMovement(addx, 2, addz);
				   dead.hurt(new DamageSource("Reaper"), 10000);
			   }
		   }
		   Explosion explosion = new Explosion(level, this, new DamageSource("reaper"), null, getX(), getEyeY() - 2, getZ(), 5, false, Explosion.BlockInteraction.NONE);
		   explosion.explode();
		   List<BlockPos> marked = explosion.getToBlow();
		   for (BlockPos pos : marked) 
		   {
			   if (pos.getY() > getEyeY() - 5)
			   {
				   level.destroyBlock(pos, true);
			   }  
		   }
		   explosion.finalizeExplosion(false);
	   }
	   
	   private ArrayList<Double> doComplexMath(int distance)
	   {
		   float rot = getYRot();
		   ArrayList<Double> bothAdds = new ArrayList<Double>();
		   double addx;
		   double addz;
		   if (rot == 0)
		   {
			   addx = distance;
			   addz = 0;
		   }
		   else if (rot < 90)
		   {
			   addx = distance * Math.sin(rot);
			   addz = Math.sqrt((distance * distance) - (addx * addx));
		   }
		   else if (rot == 90)
		   {
			   addx = 0;
			   addz = distance;
		   }
		   else if (rot > 90 && rot < 180)
		   {
			   addx = -distance * Math.sin(rot);
			   addz = Math.sqrt((distance * distance) - (addx * addx));
		   }
		   else if (rot == 180)
		   {
			   addx = -distance;
			   addz = 0;
		   }
		   else if (rot > 180 && rot < 270)
		   {
			   addx = -distance * Math.sin(rot);
			   addz = -Math.sqrt((distance * distance) - (addx * addx));
		   }
		   else if (rot == 270)
		   {
			   addx = 0;
			   addz = -distance;
		   }
		   else
		   {
			   addx = distance * Math.sin(rot);
			   addz = -Math.sqrt((distance * distance) - (addx * addx));
		   }
		   bothAdds.add(addx);
		   bothAdds.add(addz);
		   return bothAdds;
	   }
	   
	   private void multiSwipe(LivingEntity target)
	   {
		   timeBetweenSwipes += 1;
		   if (distanceToSqr(target) <= 4 && timeBetweenSwipes >= 15 && swipeNum < 3)
		   {
			   isBetweenSwipes = false;
			   swipeNum += 1;
			   timeBetweenSwipes = 0;
			   float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90;
			   this.setYRot(yaw);
			   List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.0D, 1.0D, 6.0D));
			   List<LivingEntity> hitEntities = new ArrayList<LivingEntity>();
			   for (int e = 0; e < list.size(); e++)
			   {
				   if (hasLineOfSight(list.get(e)) && !(list.get(e) instanceof Reaper)) 
				   {
	                   hitEntities.add(list.get(e));
	               }
			   }
			   for (int h = 0; h < hitEntities.size(); h++)
			   {
				   boolean noDamage = false;
				   if (hitEntities.get(h) instanceof Player)
				   {
					   Player player = (Player) hitEntities.get(h);
					   if (player.isBlocking())
					   {
						   if (swipeNum == 3)
						   {
							   player.disableShield(true);
						   }
					       noDamage = true;
					   }
				   }
				   ArrayList<Double> adds = doComplexMath(5);
				   double addx = adds.get(0);
				   double addz = adds.get(1);
				   hitEntities.get(h).setDeltaMovement(addx, 2, addz);
				   if (!noDamage)
				   {
				       hitEntities.get(h).hurt(new DamageSource("Reaper"), 20);
				   }
				   else
				   {
					   hitEntities.get(h).getUseItem().setDamageValue(hitEntities.get(h).getUseItem().getDamageValue() + 10);
				   }
			   }
			   if (swipeNum < 3)
			   {
				   isBetweenSwipes = true;
			   }
		   }
		   else if (swipeNum == 3 && timeBetweenSwipes >= 30)
		   {
			   isBetweenSwipes = true;
			   isMultiSwiping = false;
			   timeBetweenSwipes = 14;
			   swipeNum = 0;
			   multiSwipeCooldown = 0;
		   }
	   }

	   private double getHeadX() {
	      return getX();
	   }

	   private double getHeadY() {
	      return getEyeY();
	   }

	   private double getHeadZ() {
	      return getZ();
	   }

	   private void performRangedAttack(int danger, LivingEntity entity, boolean cloud) {
	      performRangedAttack(entity.getX(), entity.getY() + (double) entity.getEyeHeight() * 0.5D, entity.getZ(), danger == 1, cloud);
	   }

	   private void performRangedAttack(double x, double y, double z, boolean isDangerous, boolean cloud) {
	      if (!isSilent()) {
	         level.levelEvent((Player)null, 1024, blockPosition(), 0);
	      }

	      double d0 = getHeadX();
	      double d1 = getHeadY();
	      double d2 = getHeadZ();
	      double d3 = x - d0;
	      double d4 = y - d1;
	      double d5 = z - d2;
	      ReaperSkull reaperskull = new ReaperSkull(level, this, d3, d4, d5);
	      reaperskull.setOwner(this);
	      if (cloud)
	      {
	    	  reaperskull.addTag("cloud");
	      }
	      if (isDangerous) {
              reaperskull.setDangerous(true);
	      }
	      reaperskull.setPosRaw(d0, d1, d2);
	      level.addFreshEntity(reaperskull);
	   }

	   public void performRangedAttack(LivingEntity entity, float p_31469_, boolean cloud) {
	      performRangedAttack(0, entity, cloud);
	   }
	   
	   static class ReaperLaserGoal extends Goal {
		      private final Reaper reaper;
		      private int attackTime;
		      private int playerBlockTime;
		      private boolean playerBlocking;

		      public ReaperLaserGoal(Reaper p_32871_) {
		         reaper = p_32871_;
		         setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		      }

		      public boolean canUse() {
		         LivingEntity livingentity = reaper.getTarget();
		         return livingentity != null && livingentity.isAlive();
		      }

		      public boolean canContinueToUse() {
		         return super.canContinueToUse() && (reaper.getTarget() != null && (reaper.distanceToSqr(reaper.getTarget()) > 0.0D && !reaper.isSpinning));
		      }

		      public void start() {
		         attackTime = 0;
		         playerBlockTime = 0;
		         reaper.getNavigation().stop();
		         LivingEntity livingentity = reaper.getTarget();
		         if (livingentity != null && !reaper.isSpinning) {
		            reaper.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
		         }

		         reaper.hasImpulse = true;
		      }

		      public void stop() {
		    	 if (!reaper.isSpinning)
		    	 {
		    		 reaper.setActiveAttackTarget(0);
		             reaper.setTarget((LivingEntity)null);
		    	 }
		      }

		      public boolean requiresUpdateEveryTick() {
		         return true;
		      }

		      public void tick() {
		         LivingEntity livingentity = reaper.getTarget();
		         if (livingentity != null && !reaper.isSlamming && !reaper.isSpinning) {
		            reaper.getNavigation().stop();
		            reaper.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
		            if (reaper.hasLineOfSight(livingentity))
		            {
		               ++attackTime;
		               if (attackTime == 0) 
		               {
		                  reaper.setActiveAttackTarget(livingentity.getId());
		                  if (!reaper.isSilent()) 
		                  {
		                     reaper.level.broadcastEntityEvent(reaper, (byte)21);
		                  }
		               }
		               playerBlocking = false;
		               if (livingentity instanceof Player player && player.isBlocking())
		               {
		                   playerBlockTime += 1;
		                   playerBlocking = true;
		                   player.getUseItem().setDamageValue(player.getUseItem().getDamageValue() + 1);
		               }
		               else if (attackTime % reaper.getAttackDuration() == 0 && !playerBlocking) 
		               {
		            	   playerBlockTime = 0;
		                   float f = 1.0F;
		                   if (reaper.level.getDifficulty() == Difficulty.HARD) 
		                   {
		                       f += 0.5F;
		                   }
		                   else
		                   {
		                	   if (livingentity.getHealth() < f)
		                	   {
		                		   livingentity.hurt(DamageSource.indirectMagic(reaper, reaper), f);
		                		   reaper.setActiveAttackTarget(0);
		                	   }
		                	   else
		                	   {
		                		   livingentity.hurt(DamageSource.indirectMagic(reaper, reaper), f);
		                	   }
		                   }
		               }
		               if (playerBlockTime >= 50)
		               {
		                   reaper.playerBlockedBeam = true;
		                   playerBlockTime = 0;
		               }
		            }
		            super.tick();
		         }
		      }
		   }
	   
	   
		   
	   public boolean hurt(DamageSource p_31461_, float p_31462_) {
		  float finalDamage = p_31462_;
	      if (isInvulnerableTo(p_31461_)) {
	         return false;
	      } else if (p_31461_ != DamageSource.DROWN && !(p_31461_.getEntity() instanceof Reaper)) {
	         if (getInvulnerableTicks() > 0 && p_31461_ != DamageSource.OUT_OF_WORLD) {
	            return false;
	         } else {
	            if (isPowered()) {
	               Entity entity = p_31461_.getDirectEntity();
	               if (entity instanceof AbstractArrow) {
	                  return false;
	               }
	            }
	            Entity entity = p_31461_.getDirectEntity();
	            if (entity instanceof AbstractArrow) {
	               finalDamage /= 2;
	            }

	            Entity entity1 = p_31461_.getEntity();
	            if (entity1 != null && !(entity1 instanceof Player) && entity1 instanceof LivingEntity && ((LivingEntity)entity1).getMobType() == getMobType()) {
	               return false;
	            } else {
	               if (destroyBlocksTick <= 0) {
	                  destroyBlocksTick = 20;
	               }
	               return super.hurt(p_31461_, finalDamage);
	            }
	         }
	      } else {
	         return false;
	      }
	   }
	   
	   static class ReaperLookAtPlayerGoal extends Goal {
		   public static final float DEFAULT_PROBABILITY = 0.02F;
		   protected final Reaper mob;
		   @Nullable
		   protected Entity lookAt;
		   protected final float lookDistance;
		   private int lookTime;
		   protected final float probability;
		   private final boolean onlyHorizontal;
		   protected final Class<? extends LivingEntity> lookAtType;
		   protected final TargetingConditions lookAtContext;

		   public ReaperLookAtPlayerGoal(Reaper pMob, Class<? extends LivingEntity> pLookAtType, float pLookDistance) {
		      this(pMob, pLookAtType, pLookDistance, 0.02F);
		   }

		   public ReaperLookAtPlayerGoal(Reaper pMob, Class<? extends LivingEntity> pLookAtType, float pLookDistance, float pProbability) {
		      this(pMob, pLookAtType, pLookDistance, pProbability, false);
		   }

		   public ReaperLookAtPlayerGoal(Reaper pMob, Class<? extends LivingEntity> pLookAtType, float pLookDistance, float pProbability, boolean pOnlyHorizontal) {
		      mob = pMob;
		      lookAtType = pLookAtType;
		      lookDistance = pLookDistance;
		      probability = pProbability;
		      onlyHorizontal = pOnlyHorizontal;
		      setFlags(EnumSet.of(Goal.Flag.LOOK));
		      if (pLookAtType == Player.class && !mob.isSpinning) {
		         lookAtContext = TargetingConditions.forNonCombat().range((double)pLookDistance).selector((p_25531_) -> {
		            return EntitySelector.notRiding(pMob).test(p_25531_);
		         });
		      } else {
		         lookAtContext = TargetingConditions.forNonCombat().range((double)pLookDistance);
		      }

		   }

		   /**
		    * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
		    * method as well.
		    */
		   public boolean canUse() {
			  boolean retVal = false;
		      if (mob.getRandom().nextFloat() >= probability) {
		         retVal = false;
		      } else if (!mob.isSpinning){
		         if (mob.getTarget() != null) {
		            lookAt = mob.getTarget();
		         }

		         if (lookAtType == Player.class) {
		            lookAt = mob.level.getNearestPlayer(lookAtContext, mob, mob.getX(), mob.getEyeY(), mob.getZ());
		         } else {
		            lookAt = mob.level.getNearestEntity(mob.level.getEntitiesOfClass(lookAtType, mob.getBoundingBox().inflate((double)lookDistance, 3.0D, (double)lookDistance), (p_148124_) -> {
		               return true;
		            }), lookAtContext, mob, mob.getX(), mob.getEyeY(), mob.getZ());
		         }

		         retVal = lookAt != null;
		      }
		      return retVal;
		   }

		   /**
		    * Returns whether an in-progress EntityAIBase should continue executing
		    */
		   public boolean canContinueToUse() {
			  boolean retVal = false;
		      if (!lookAt.isAlive() || mob.isSpinning) {
		    	  retVal = false;
		      } else if (mob.distanceToSqr(lookAt) > (double)(lookDistance * lookDistance) || mob.isSpinning) {
		    	  retVal = false;
		      } else if (!mob.isSpinning) {
		    	  retVal = lookTime > 0;
		      }
		      return retVal;
		   }

		   /**
		    * Execute a one shot task or start executing a continuous task
		    */
		   public void start() {
		      lookTime = adjustedTickDelay(40 + mob.getRandom().nextInt(40));
		   }

		   /**
		    * Reset the task's internal state. Called when this task is interrupted by another one
		    */
		   public void stop() {
		      lookAt = null;
		   }

		   /**
		    * Keep ticking a continuous task that has already been started
		    */
		   public void tick() {
		      if (lookAt.isAlive() && !mob.isSpinning) {
		         double d0 = onlyHorizontal ? mob.getEyeY() : lookAt.getEyeY();
		         mob.getLookControl().setLookAt(lookAt.getX(), d0, lookAt.getZ());
		         --lookTime;
		      }
		   }
		}

	   protected void dropCustomDeathLoot(DamageSource source, int p_31465_, boolean p_31466_) {
	      super.dropCustomDeathLoot(source, p_31465_, p_31466_);
	      ItemEntity itementity = spawnAtLocation(Items.NETHER_STAR);
	      if (itementity != null) {
	         itementity.setExtendedLifetime();
	      }

	   }

	   public void checkDespawn() {
	      if (level.getDifficulty() == Difficulty.PEACEFUL && shouldDespawnInPeaceful()) {
	         discard();
	      } else {
	         noActionTime = 0;
	      }
	   }

	   public boolean causeFallDamage(float p_149589_, float p_149590_, DamageSource p_149591_) {
	      return false;
	   }

	   public boolean addEffect(MobEffectInstance p_182397_, @Nullable Entity p_182398_) {
	      return false;
	   }

	   public static AttributeSupplier.Builder createAttributes() {
	      return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 500.0D).add(Attributes.MOVEMENT_SPEED, (double)1.2F).add(Attributes.FLYING_SPEED, (double)1.2F).add(Attributes.FOLLOW_RANGE, 80.0D).add(Attributes.ARMOR, 6.0D);
	   }

	   public int getInvulnerableTicks() {
	      return entityData.get(DATA_ID_INV);
	   }

	   public void setInvulnerableTicks(int p_31511_) {
	      entityData.set(DATA_ID_INV, p_31511_);
	   }

	   public boolean isPowered() {
	      return getHealth() <= getMaxHealth() / 2.0F;
	   }

	   public MobType getMobType() {
	      return MobType.UNDEAD;
	   }

	   protected boolean canRide(Entity p_31508_) {
	      return false;
	   }
	   
	   void setActiveAttackTarget(int p_32818_) {
		   entityData.set(DATA_ID_ATTACK_TARGET, p_32818_);
	   }

	   public boolean hasActiveAttackTarget() {
		   return entityData.get(DATA_ID_ATTACK_TARGET) != 0;
	   }
	   
	   @Nullable
	   public LivingEntity getActiveAttackTarget() {
	      if (!hasActiveAttackTarget()) {
	         return null;
	      } else if (level.isClientSide) {
	         if (clientSideCachedAttackTarget != null) {
	            return clientSideCachedAttackTarget;
	         } else {
	            Entity entity = level.getEntity(entityData.get(DATA_ID_ATTACK_TARGET));
	            if (entity instanceof LivingEntity) {
	               clientSideCachedAttackTarget = (LivingEntity)entity;
	               return clientSideCachedAttackTarget;
	            } else {
	               return null;
	            }
	         }
	      } else {
	         return getTarget();
	      }
	   }

	   public boolean canChangeDimensions() {
	      return false;
	   }

	   public boolean canBeAffected(MobEffectInstance p_31495_) {
	      return (p_31495_.getEffect() == LavaEffects.SOUL_FLAME.get() || p_31495_.getEffect() == LavaEffects.CRIPPLING.get()) ? false : super.canBeAffected(p_31495_);
	   }

	   class ReaperDoNothingGoal extends Goal {
	      public ReaperDoNothingGoal() {
	         setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
	      }

	      public boolean canUse() {
	         return Reaper.this.getInvulnerableTicks() > 0;
	      }
	   }
	}