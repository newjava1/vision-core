package org.vision.common.runtime;

import static org.vision.common.runtime.VvmTestUtils.generateDeploySmartContractAndGetTransaction;
import static org.vision.common.runtime.VvmTestUtils.generateTriggerSmartContractAndGetTransaction;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.actuator.VMActuator;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.ContractCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.core.db.TransactionContext;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.ReceiptCheckErrException;
import org.vision.core.exception.VMIllegalException;
import org.vision.core.store.StoreFactory;
import org.vision.core.vm.repository.Repository;
import org.vision.core.vm.repository.RepositoryImpl;
import org.vision.common.application.Application;
import org.vision.common.application.ApplicationFactory;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;


@Slf4j

public class RuntimeImplTest {

  private Manager dbManager;
  private VisionApplicationContext context;
  private Repository repository;
  private String dbPath = "output_RuntimeImplTest";
  private Application AppT;
  private byte[] callerAddress;
  private long callerTotalBalance = 4_000_000_000L;
  private byte[] creatorAddress;
  private long creatorTotalBalance = 3_000_000_000L;

  /**
   * Init data.
   */
  @Before
  public void init() {
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
    callerAddress = Hex
        .decode(Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc");
    creatorAddress = Hex
        .decode(Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abd");
    dbManager = context.getBean(Manager.class);
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    dbManager.getDynamicPropertiesStore().saveTotalEntropyWeight(5_000_000_000L); // unit is trx
    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    repository.createAccount(callerAddress, AccountType.Normal);
    repository.addBalance(callerAddress, callerTotalBalance);
    repository.createAccount(creatorAddress, AccountType.Normal);
    repository.addBalance(creatorAddress, creatorTotalBalance);
    repository.commit();
  }

  // // solidity src code
  // pragma solidity ^0.4.2;
  //
  // contract TestEntropyLimit {
  //
  //   function testNotConstant(uint256 count) {
  //     uint256 curCount = 0;
  //     while(curCount < count) {
  //       uint256 a = 1;
  //       curCount += 1;
  //     }
  //   }
  //
  //   function testConstant(uint256 count) constant {
  //     uint256 curCount = 0;
  //     while(curCount < count) {
  //       uint256 a = 1;
  //       curCount += 1;
  //     }
  //   }
  //
  // }


  @Test
  public void getCreatorEntropyLimit2Test() throws ContractValidateException, ContractExeException {

    long value = 10L;
    long feeLimit = 1_000_000_000L;
    long consumeUserResourcePercent = 0L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],\""
        + "name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"view"
        + "\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\",\"type"
        + "\":\"uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\":false,\""
        + "stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b506076600"
        + "4803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a0600480"
        + "3603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf576001905"
        + "060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc56"
        + "5b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce25765"
        + "4c8c17b0029";
    String libraryAddressPair = null;

    Transaction trx = VvmTestUtils.generateDeploySmartContractAndGetTransaction(contractName, creatorAddress,
        ABI,
        code, value, feeLimit, consumeUserResourcePercent, libraryAddressPair);

    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(trx),
            StoreFactory.getInstance(), true, true));

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);

    long expectEntropyLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountEntropyLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectEntropyLimit1);

    value = 2_500_000_000L;
    long expectEntropyLimit2 = 5_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountEntropyLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectEntropyLimit2);

    value = 10L;
    feeLimit = 1_000_000L;
    long expectEntropyLimit3 = 10_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountEntropyLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectEntropyLimit3);

    long frozenBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - frozenBalance;
    creatorAccount.setFrozenForEntropy(frozenBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 1_000_000_000L;
    long expectEntropyLimit4 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountEntropyLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectEntropyLimit4);

    feeLimit = 3_000_000_000L;
    value = 10L;
    long expectEntropyLimit5 = 20_009_999L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountEntropyLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectEntropyLimit5);

    feeLimit = 3_000L;
    value = 10L;
    long expectEntropyLimit6 = 30L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountEntropyLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectEntropyLimit6);

  }

  @Test
  public void getCallerAndCreatorEntropyLimit2With0PercentTest()
      throws ContractExeException, ReceiptCheckErrException, VMIllegalException,
      ContractValidateException {

    long value = 0;
    long feeLimit = 1_000_000_000L; // vdt
    long consumeUserResourcePercent = 0L;
    long creatorEntropyLimit = 5_000L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],"
        + "\"name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\""
        + "view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\","
        + "\"type\":\"uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\""
        + ":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b506076600"
        + "4803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a0600480"
        + "3603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf576001905"
        + "060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc56"
        + "5b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce25765"
        + "4c8c17b0029";
    String libraryAddressPair = null;
    VVMTestResult result = VvmTestUtils
        .deployContractWithCreatorEntropyLimitAndReturnVvmTestResult(contractName, creatorAddress,
            ABI, code, value,
            feeLimit, consumeUserResourcePercent, libraryAddressPair, dbManager, null,
            creatorEntropyLimit);

    byte[] contractAddress = result.getContractAddress();
    byte[] triggerData = VvmTestUtils.parseAbi("testNotConstant()", null);
    Transaction trx = VvmTestUtils.generateTriggerSmartContractAndGetTransaction(callerAddress, contractAddress,
        triggerData, value, feeLimit);

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(trx),
            StoreFactory.getInstance(), true, true));

    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);
    AccountCapsule callerAccount = repository.getAccount(callerAddress);
    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(trx);

    feeLimit = 1_000_000_000L;
    value = 0L;
    long expectEntropyLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit1);

    long creatorFrozenBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - creatorFrozenBalance;
    creatorAccount.setFrozenForEntropy(creatorFrozenBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 1_000_000_000L;
    value = 0L;
    long expectEntropyLimit2 = 10_005_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit2);

    value = 3_500_000_000L;
    long expectEntropyLimit3 = 5_005_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit3);

    value = 10L;
    feeLimit = 5_000_000_000L;
    long expectEntropyLimit4 = 40_004_999L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit4);

    long callerFrozenBalance = 1_000_000_000L;
    callerAccount.setFrozenForEntropy(callerFrozenBalance, 0L);
    callerAccount.setBalance(callerAccount.getBalance() - callerFrozenBalance);
    repository.putAccountValue(callerAddress, callerAccount);
    repository.commit();

    value = 10L;
    feeLimit = 5_000_000_000L;
    long expectEntropyLimit5 = 30_014_999L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit5);

  }

  @Test
  public void getCallerAndCreatorEntropyLimit2With40PercentTest()
      throws ContractExeException, ReceiptCheckErrException, VMIllegalException,
      ContractValidateException {

    long value = 0;
    long feeLimit = 1_000_000_000L; // vdt
    long consumeUserResourcePercent = 40L;
    long creatorEntropyLimit = 5_000L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],\""
        + "name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"view\""
        + ",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\",\"type\":"
        + "\"uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\":false,\""
        + "stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b50607660"
        + "04803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a060048"
        + "03603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf57600190"
        + "5060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc5"
        + "65b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce2576"
        + "54c8c17b0029";
    String libraryAddressPair = null;
    VVMTestResult result = VvmTestUtils
        .deployContractWithCreatorEntropyLimitAndReturnVvmTestResult(contractName, creatorAddress,
            ABI, code, value,
            feeLimit, consumeUserResourcePercent, libraryAddressPair, dbManager, null,
            creatorEntropyLimit);

    byte[] contractAddress = result.getContractAddress();
    byte[] triggerData = VvmTestUtils.parseAbi("testNotConstant()", null);
    Transaction trx = VvmTestUtils.generateTriggerSmartContractAndGetTransaction(callerAddress, contractAddress,
        triggerData, value, feeLimit);

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(trx),
            StoreFactory.getInstance(), true, true));

    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);
    AccountCapsule callerAccount = repository.getAccount(callerAddress);
    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(trx);

    feeLimit = 1_000_000_000L;
    value = 0L;
    long expectEntropyLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit1);

    long creatorFrozenBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - creatorFrozenBalance;
    creatorAccount.setFrozenForEntropy(creatorFrozenBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 1_000_000_000L;
    value = 0L;
    long expectEntropyLimit2 = 10_005_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit2);

    value = 3_999_950_000L;
    long expectEntropyLimit3 = 1_250L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit3);

  }

  @Test
  public void getCallerAndCreatorEntropyLimit2With100PercentTest()
      throws ContractExeException, ReceiptCheckErrException, VMIllegalException,
      ContractValidateException {

    long value = 0;
    long feeLimit = 1_000_000_000L; // vdt
    long consumeUserResourcePercent = 100L;
    long creatorEntropyLimit = 5_000L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],"
        + "\"name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"view\""
        + ",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\",\"type\":\""
        + "uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\":false,\""
        + "stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b506076600"
        + "4803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a0600480"
        + "3603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf576001905"
        + "060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc56"
        + "5b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce25765"
        + "4c8c17b0029";
    String libraryAddressPair = null;
    VVMTestResult result = VvmTestUtils
        .deployContractWithCreatorEntropyLimitAndReturnVvmTestResult(contractName, creatorAddress,
            ABI, code, value,
            feeLimit, consumeUserResourcePercent, libraryAddressPair, dbManager, null,
            creatorEntropyLimit);

    byte[] contractAddress = result.getContractAddress();
    byte[] triggerData = VvmTestUtils.parseAbi("testNotConstant()", null);
    Transaction trx = VvmTestUtils.generateTriggerSmartContractAndGetTransaction(callerAddress, contractAddress,
        triggerData, value, feeLimit);

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(trx),
            StoreFactory.getInstance(), true, true));

    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);
    AccountCapsule callerAccount = repository.getAccount(callerAddress);
    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(trx);

    feeLimit = 1_000_000_000L;
    value = 0L;
    long expectEntropyLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit1);

    long creatorFrozenBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - creatorFrozenBalance;
    creatorAccount.setFrozenForEntropy(creatorFrozenBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 1_000_000_000L;
    value = 0L;
    long expectEntropyLimit2 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit2);

    value = 3_999_950_000L;
    long expectEntropyLimit3 = 500L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalEntropyLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectEntropyLimit3);

  }

  /**
   * Release resources.
   */
  @After
  public void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }
}

