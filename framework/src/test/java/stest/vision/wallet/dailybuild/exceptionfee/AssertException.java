package stest.vision.wallet.dailybuild.exceptionfee;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI.AccountResourceMessage;
import org.vision.api.WalletGrpc;
import org.vision.api.WalletSolidityGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Result.contractResult;
import org.vision.protos.Protocol.TransactionInfo;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class AssertException {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelSolidity = null;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
  }

  @Test(enabled = true, description = "Trigger contract Divide 0")
  public void test1DivideInt() {
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 100000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String filePath = "src/test/resources/soliditycode/assertExceptiontest1DivideInt.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    Account info;
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";
    String num = "4" + "," + "0";

    txid = PublicMethed.triggerContract(contractAddress,
        "divideIHaveArgsReturn(int256,int256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infoById:" + infoById);
    Optional<Transaction> ById = PublicMethed.getTransactionById(txid, blockingStubFull);
    logger.info("getRet:" + ById.get().getRet(0));
    logger.info("getNumber:" + ById.get().getRet(0).getContractRet().getNumber());
    logger.info("getContractRetValue:" + ById.get().getRet(0).getContractRetValue());
    logger.info("getContractRet:" + ById.get().getRet(0).getContractRet());

    Assert.assertEquals(ById.get().getRet(0).getContractRet().getNumber(),
        contractResult.ILLEGAL_OPERATION_VALUE);
    Assert.assertEquals(ById.get().getRet(0).getContractRetValue(), 8);
    Assert.assertEquals(ById.get().getRet(0).getContractRet(), contractResult.ILLEGAL_OPERATION);

    Assert
        .assertEquals(ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray()), "");
    Assert.assertEquals(contractResult.ILLEGAL_OPERATION, infoById.get().getReceipt().getResult());

    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertTrue(afterBalance + maxFeeLimit + photonFee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);
  }

  @Test(enabled = true, description = "Trigger contract index out of bounds")
  public void test2FindArgsContractMinTest() {
    String filePath =
        "src/test/resources/soliditycode/assertExceptiontest2FindArgsContractMinTest.sol";
    String contractName = "findArgsIContract";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("11:" + Base58.encode58Check(contractAddress));
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";
    Integer triggerNum = -1;
    txid = PublicMethed.triggerContract(contractAddress,
        "findArgsByIndex1(uint256)", triggerNum.toString(), false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertTrue(afterBalance + maxFeeLimit + photonFee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);

  }

  @Test(enabled = true, description = "Trigger contract Bytes array index out of bounds")
  public void test3ByteMinContract() {
    String filePath = "src/test/resources/soliditycode/assertExceptiontest3ByteMinContract.sol";
    String contractName = "byteContract";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";
    Integer triggerNum = -1;
    txid = PublicMethed.triggerContract(contractAddress,
        "testBytesGet(uint256)", triggerNum.toString(), false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertTrue(afterBalance + maxFeeLimit + photonFee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);

  }

  @Test(enabled = true, description = "Trigger contract convert too large value to enumerated type")
  public void test4Enum() {
    String filePath = "src/test/resources/soliditycode/assertExceptiontest4Enum.sol";
    String contractName = "enumContract";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";
    Integer triggerNum = 22;

    txid = PublicMethed.triggerContract(contractAddress,
        "setGoStraight(uint8)", triggerNum.toString(), false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Optional<TransactionInfo> infoById = null;

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertTrue(afterBalance + maxFeeLimit + photonFee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);
  }

  @Test(enabled = true, description = "Trigger contract move a negative value to a binary")
  public void test5MoveRight() {
    String filePath = "src/test/resources/soliditycode/assertExceptiontest5MoveRight.sol";
    String contractName = "binaryRightContract";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);

    Account info;

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";
    Integer triggerNum = -1;
    txid = PublicMethed.triggerContract(contractAddress,
        "binaryMoveR(int256)", triggerNum.toString(), false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertTrue(afterBalance + maxFeeLimit + photonFee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);
  }

  @Test(enabled = true, description = "Trigger contract Call an uninitialized "
      + "internal function type variable")
  public void test6UninitializedContract() {
    String filePath =
        "src/test/resources/soliditycode/assertExceptiontest6UninitializedContract.sol";
    String contractName = "uni";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";

    txid = PublicMethed.triggerContract(contractAddress,
        "test2()", "#", false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertTrue(afterBalance + maxFeeLimit + photonFee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);

  }

  @Test(enabled = true, description = "Trigger contract assert exception")
  public void test7TestAssertContract() {
    String filePath = "src/test/resources/soliditycode/assertExceptiontest7TestAssertContract.sol";
    String contractName = "TestThrowsContract";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();

    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    String txid = "";
    txid = PublicMethed.triggerContract(contractAddress,
        "testAssert()", "#", false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertTrue((beforeBalance - maxFeeLimit - photonFee) == afterBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed
        .freedResource(contractExcAddress, contractExcKey, testNetAccountAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}
