package org.vision.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.vision.common.application.VisionApplicationContext;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.capsule.*;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.Protocol.Vote;
import org.vision.protos.contract.AssetIssueContractOuterClass;
import org.vision.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.vision.protos.contract.Common.ResourceCode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.fail;

@Slf4j
public class UnfreezeBalanceActuatorTest {

  private static final String dbPath = "output_unfreeze_balance_test";
  private static final String OWNER_ADDRESS;
  private static final String RECEIVER_ADDRESS;
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ACCOUNT_INVALID;
  private static final long initBalance = 10_000_000_000L;
  private static final long frozenBalance = 1_000_000_000L;
  private static final long smallTatalResource = 100L;
  private static Manager dbManager;
  private static VisionApplicationContext context;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    RECEIVER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
    OWNER_ACCOUNT_INVALID =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    //    Args.setParam(new String[]{"--output-directory", dbPath},
    //        "config-junit.conf");
    //    dbManager = new Manager();
    //    dbManager.init();
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createAccountCapsule() {
    AccountCapsule ownerCapsule = new AccountCapsule(ByteString.copyFromUtf8("owner"),
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), AccountType.Normal,
        initBalance);
    dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);

    AccountCapsule receiverCapsule = new AccountCapsule(ByteString.copyFromUtf8("receiver"),
        ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)), AccountType.Normal,
        initBalance);
    dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);
  }

  private Any getContractForPhoton(String ownerAddress) {
    return Any.pack(UnfreezeBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress))).build());
  }

  private Any getContractForCpu(String ownerAddress) {
    return Any.pack(UnfreezeBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setResource(ResourceCode.ENTROPY).build());
  }

  private Any getDelegatedContractForPhoton(String ownerAddress, String receiverAddress) {
    return Any.pack(UnfreezeBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress))).build());
  }

  private Any getDelegatedContractForCpu(String ownerAddress, String receiverAddress) {
    return Any.pack(UnfreezeBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
        .setResource(ResourceCode.ENTROPY).build());
  }

  private Any getContract(String ownerAddress, ResourceCode resourceCode) {
    return Any.pack(UnfreezeBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setResource(resourceCode).build());
  }


  @Test
  public void testUnfreezeBalanceForPhoton() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setFrozen(frozenBalance, now);
    Assert.assertEquals(accountCapsule.getFrozenBalance(), frozenBalance);
    Assert.assertEquals(accountCapsule.getVisionPower(), frozenBalance);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    long totalPhotonWeightBefore = dbManager.getDynamicPropertiesStore().getTotalPhotonWeight();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance + frozenBalance);
      Assert.assertEquals(owner.getFrozenBalance(), 0);
      Assert.assertEquals(owner.getVisionPower(), 0L);

      long totalPhotonWeightAfter = dbManager.getDynamicPropertiesStore().getTotalPhotonWeight();
      Assert.assertEquals(totalPhotonWeightBefore,
          totalPhotonWeightAfter + frozenBalance / 1000_000L);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testUnfreezeBalanceForEntropy() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setFrozenForEntropy(frozenBalance, now);
    Assert.assertEquals(accountCapsule.getAllFrozenBalanceForEntropy(), frozenBalance);
    Assert.assertEquals(accountCapsule.getVisionPower(), frozenBalance);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForCpu(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    long totalEntropyWeightBefore = dbManager.getDynamicPropertiesStore().getTotalEntropyWeight();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance + frozenBalance);
      Assert.assertEquals(owner.getEntropyFrozenBalance(), 0);
      Assert.assertEquals(owner.getVisionPower(), 0L);
      long totalEntropyWeightAfter = dbManager.getDynamicPropertiesStore().getTotalEntropyWeight();
      Assert.assertEquals(totalEntropyWeightBefore,
          totalEntropyWeightAfter + frozenBalance / 1000_000L);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUnfreezeDelegatedBalanceForPhoton() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(frozenBalance, owner.getVisionPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(0L, receiver.getVisionPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setFrozenBalanceForPhoton(frozenBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForPhoton(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      AccountCapsule receiverResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(RECEIVER_ADDRESS));

      Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getVisionPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForPhoton());
      Assert.assertEquals(0L, receiverResult.getAllFrozenBalanceForPhoton());

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUnfreezeDelegatedBalanceForPhotonWithDeletedReceiver() {

    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(frozenBalance, owner.getVisionPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(0L, receiver.getVisionPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setFrozenBalanceForPhoton(frozenBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForPhoton(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getDynamicPropertiesStore().saveAllowVvmConstantinople(0);
    dbManager.getAccountStore().delete(receiver.createDbKey());
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "Receiver Account[a0abd4b9367799eaa3197fecb144eb71de1e049150] does not exist");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowVvmConstantinople(1);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getVisionPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForPhoton());

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert
          .assertEquals(0,
              delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());

    } catch (ContractValidateException e) {
      logger.error("", e);
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  @Test
  public void testUnfreezeDelegatedBalanceForPhotonWithRecreatedReceiver() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    dbManager.getDynamicPropertiesStore().saveAllowVvmConstantinople(1);

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(frozenBalance, owner.getVisionPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(0L, receiver.getVisionPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(),
        receiver.getAddress()
    );
    delegatedResourceCapsule.setFrozenBalanceForPhoton(
        frozenBalance,
        now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForPhoton(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getAccountStore().delete(receiver.createDbKey());
    receiver = new AccountCapsule(receiver.getAddress(), ByteString.EMPTY, AccountType.Normal);
    receiver.setAcquiredDelegatedFrozenBalanceForPhoton(10L);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);
    receiver = dbManager.getAccountStore().get(receiver.createDbKey());
    Assert.assertEquals(10, receiver.getAcquiredDelegatedFrozenBalanceForPhoton());

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(0);
    dbManager.getDynamicPropertiesStore().saveAllowVvmSolidity059(0);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "AcquiredDelegatedFrozenBalanceForPhoton[10] < delegatedPhoton[1000000000]");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(1);
    dbManager.getDynamicPropertiesStore().saveAllowVvmSolidity059(1);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getVisionPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForPhoton());

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());

      receiver = dbManager.getAccountStore().get(receiver.createDbKey());
      Assert.assertEquals(0, receiver.getAcquiredDelegatedFrozenBalanceForPhoton());

    } catch (ContractValidateException e) {
      logger.error("", e);
      Assert.fail();
    } catch (ContractExeException e) {
      Assert.fail();
    }
  }

  /**
   * when SameTokenName close,delegate balance frozen, unfreoze show error
   */
  @Test
  public void testUnfreezeDelegatedBalanceForPhotonSameTokenNameClose() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(0);

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(frozenBalance, owner.getVisionPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedFrozenBalanceForPhoton(frozenBalance);
    Assert.assertEquals(0L, receiver.getVisionPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setFrozenBalanceForPhoton(frozenBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForPhoton(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("no frozenBalance(PHOTON)", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUnfreezeDelegatedBalanceForCpu() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.addDelegatedFrozenBalanceForEntropy(frozenBalance);
    Assert.assertEquals(frozenBalance, owner.getVisionPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.addAcquiredDelegatedFrozenBalanceForEntropy(frozenBalance);
    Assert.assertEquals(0L, receiver.getVisionPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setFrozenBalanceForEntropy(frozenBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      AccountCapsule receiverResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(RECEIVER_ADDRESS));

      Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getVisionPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForEntropy());
      Assert.assertEquals(0L, receiverResult.getAllFrozenBalanceForEntropy());
    } catch (ContractValidateException e) {
      logger.error("", e);
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUnfreezeDelegatedBalanceForCpuWithDeletedReceiver() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.addDelegatedFrozenBalanceForEntropy(frozenBalance);
    Assert.assertEquals(frozenBalance, owner.getVisionPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.addAcquiredDelegatedFrozenBalanceForEntropy(frozenBalance);
    Assert.assertEquals(0L, receiver.getVisionPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setFrozenBalanceForEntropy(frozenBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getDynamicPropertiesStore().saveAllowVvmConstantinople(0);
    dbManager.getAccountStore().delete(receiver.createDbKey());

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "Receiver Account[a0abd4b9367799eaa3197fecb144eb71de1e049150] does not exist");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowVvmConstantinople(1);

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getVisionPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForEntropy());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUnfreezeDelegatedBalanceForCpuWithRecreatedReceiver() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    dbManager.getDynamicPropertiesStore().saveAllowVvmConstantinople(1);

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.addDelegatedFrozenBalanceForEntropy(frozenBalance);
    Assert.assertEquals(frozenBalance, owner.getVisionPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.addAcquiredDelegatedFrozenBalanceForEntropy(frozenBalance);
    Assert.assertEquals(0L, receiver.getVisionPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(),
        receiver.getAddress()
    );
    delegatedResourceCapsule.setFrozenBalanceForEntropy(
        frozenBalance,
        now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(0);
    dbManager.getDynamicPropertiesStore().saveAllowVvmSolidity059(0);
    dbManager.getAccountStore().delete(receiver.createDbKey());
    receiver = new AccountCapsule(receiver.getAddress(), ByteString.EMPTY, AccountType.Normal);
    receiver.setAcquiredDelegatedFrozenBalanceForEntropy(10L);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);
    receiver = dbManager.getAccountStore().get(receiver.createDbKey());
    Assert.assertEquals(10, receiver.getAcquiredDelegatedFrozenBalanceForEntropy());

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "AcquiredDelegatedFrozenBalanceForEntropy[10] < delegatedEntropy[1000000000]");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(1);
    dbManager.getDynamicPropertiesStore().saveAllowVvmSolidity059(1);

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getVisionPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForEntropy());
      receiver = dbManager.getAccountStore().get(receiver.createDbKey());
      Assert.assertEquals(0, receiver.getAcquiredDelegatedFrozenBalanceForEntropy());
    } catch (ContractValidateException e) {
      Assert.fail();
    } catch (ContractExeException e) {
      Assert.fail();
    }
  }

  @Test
  public void invalidOwnerAddress() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setFrozen(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS_INVALID));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);

      Assert.assertEquals("Invalid address", e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }

  }

  @Test
  public void invalidOwnerAccount() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setFrozen(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ACCOUNT_INVALID));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ACCOUNT_INVALID + "] does not exist",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void noFrozenBalance() {
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("no frozenBalance(PHOTON)", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void notTimeToUnfreeze() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setFrozen(1_000_000_000L, now + 60000);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("It's not time to unfreeze(PHOTON).", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testClearVotes() {
    byte[] ownerAddressBytes = ByteArray.fromHexString(OWNER_ADDRESS);
    ByteString ownerAddress = ByteString.copyFrom(ownerAddressBytes);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddressBytes);
    accountCapsule.setFrozen(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getVotesStore().reset();
    Assert.assertNull(dbManager.getVotesStore().get(ownerAddressBytes));
    try {
      actuator.validate();
      actuator.execute(ret);
      VotesCapsule votesCapsule = dbManager.getVotesStore().get(ownerAddressBytes);
      Assert.assertNotNull(votesCapsule);
      Assert.assertEquals(0, votesCapsule.getNewVotes().size());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    // if had votes
    List<Vote> oldVotes = new ArrayList<Vote>();
    VotesCapsule votesCapsule = new VotesCapsule(
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), oldVotes);
    votesCapsule.addNewVotes(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
        100, 0);
    dbManager.getVotesStore().put(ByteArray.fromHexString(OWNER_ADDRESS), votesCapsule);
    accountCapsule.setFrozen(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    try {
      actuator.validate();
      actuator.execute(ret);
      votesCapsule = dbManager.getVotesStore().get(ownerAddressBytes);
      Assert.assertNotNull(votesCapsule);
      Assert.assertEquals(0, votesCapsule.getNewVotes().size());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  @Test
  public void commonErrorCheck() {
    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error, expected type [UnfreezeBalanceContract], real type[");
    actuatorTest.invalidContractType();

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setFrozen(frozenBalance, now);
    Assert.assertEquals(accountCapsule.getFrozenBalance(), frozenBalance);
    Assert.assertEquals(accountCapsule.getVisionPower(), frozenBalance);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    actuatorTest.setContract(getContractForPhoton(OWNER_ADDRESS));
    actuatorTest.nullTransationResult();

    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }

}

