package stest.vision.wallet.dailybuild.vvmnewcommand.transferfailed;

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
import org.vision.api.GrpcAPI.Return.response_code;
import org.vision.api.GrpcAPI.TransactionExtention;
import org.vision.api.WalletGrpc;
import org.vision.api.WalletSolidityGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.TransactionInfo;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class TransferFailed005 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  private final Long maxFeeLimit = Configuration.getByPath("testng.cong")
      .getLong("defaultParameter.maxFeeLimit");
  byte[] contractAddress = null;
  byte[] contractAddress1 = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] accountExcAddress = ecKey1.getAddress();
  String accountExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelSolidity = null;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(accountExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1).usePlaintext(true).build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    {
      Assert.assertTrue(PublicMethed
          .sendcoin(accountExcAddress, 10000_000_000L, testNetAccountAddress, testNetAccountKey,
              blockingStubFull));
      PublicMethed.waitProduceNextBlock(blockingStubFull);

      String filePath = "src/test/resources/soliditycode/TransferFailed005.sol";
      String contractName = "EntropyOfTransferFailedTest";
      HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
      String code = retMap.get("byteCode").toString();
      String abi = retMap.get("abI").toString();

      contractAddress = PublicMethed
          .deployContract(contractName, abi, code, "", maxFeeLimit, 100L, 100L, null, accountExcKey,
              accountExcAddress, blockingStubFull);

      filePath = "src/test/resources/soliditycode/TransferFailed005.sol";
      contractName = "Caller";
      retMap = PublicMethed.getBycodeAbi(filePath, contractName);
      code = retMap.get("byteCode").toString();
      abi = retMap.get("abI").toString();

      contractAddress1 = PublicMethed
          .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100L, null, accountExcKey,
              accountExcAddress, blockingStubFull);
    }
  }

  @Test(enabled = false, description = "Deploy contract for trigger")
  public void deployContract() {
    Assert.assertTrue(PublicMethed
        .sendcoin(accountExcAddress, 10000_000_000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String filePath = "src/test/resources/soliditycode/TransferFailed005.sol";
    String contractName = "EntropyOfTransferFailedTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100L, null, accountExcKey,
            accountExcAddress, blockingStubFull);
    String Txid1 = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit, 0L, 100L,
            null, accountExcKey, accountExcAddress, blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(Txid1, blockingStubFull);
    contractAddress = infoById.get().getContractAddress().toByteArray();
    Assert.assertEquals(0, infoById.get().getResultValue());

    filePath = "src/test/resources/soliditycode/TransferFailed005.sol";
    contractName = "Caller";
    retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    code = retMap.get("byteCode").toString();
    abi = retMap.get("abI").toString();

    contractAddress1 = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100L, null, accountExcKey,
            accountExcAddress, blockingStubFull);
    Txid1 = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit, 0L, 100L,
            null, accountExcKey, accountExcAddress, blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(Txid1, blockingStubFull);
    contractAddress1 = infoById.get().getContractAddress().toByteArray();
    logger.info("caller address : " + Base58.encode58Check(contractAddress1));
    Assert.assertEquals(0, infoById.get().getResultValue());
  }

  @Test(enabled = true, description = "TransferFailed for function call_value ")
  public void triggerContract01() {
    Account info = null;

    AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(accountExcAddress, blockingStubFull);
    info = PublicMethed.queryAccount(accountExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    //Assert.assertTrue(PublicMethed
    //    .sendcoin(contractAddress, 1000100L, accountExcAddress, accountExcKey, blockingStubFull));
    //Assert.assertTrue(PublicMethed
    //    .sendcoin(contractAddress1, 1, accountExcAddress, accountExcKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    logger.info("contractAddress balance before: " + PublicMethed
        .queryAccount(contractAddress, blockingStubFull).getBalance());
    logger.info("callerAddress balance before: " + PublicMethed
        .queryAccount(contractAddress1, blockingStubFull).getBalance());
    long paramValue = 1;

    // transfer trx to self`s account
    String param = "\"" + paramValue + "\",\"" + Base58.encode58Check(contractAddress) + "\"";
    String triggerTxid = PublicMethed
        .triggerContract(contractAddress, "testCallVsInsufficientBalance(uint256,address)", param,
            false, 0L, maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);

    Assert.assertEquals(infoById.get().getResultValue(), 1);
    Assert.assertEquals("FAILED", infoById.get().getResult().toString());
    Assert.assertEquals("TRANSFER_FAILED", infoById.get().getReceipt().getResult().toString());
    Assert.assertEquals("transfer trx failed: Cannot transfer VS to yourself.",
        infoById.get().getResMessage().toStringUtf8());
    Assert.assertEquals(100L,
        PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertEquals(0L,
        PublicMethed.queryAccount(contractAddress1, blockingStubFull).getBalance());
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);

    // transfer trx to unactivate account
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] accountExcAddress2 = ecKey2.getAddress();
    param = "\"" + paramValue + "\",\"" + Base58.encode58Check(accountExcAddress2) + "\"";
    triggerTxid = PublicMethed
        .triggerContract(contractAddress, "testCallVsInsufficientBalance(uint256,address)", param,
            false, 0L, maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);

    Assert.assertEquals(infoById.get().getResultValue(), 0);
    Assert.assertEquals("SUCESS", infoById.get().getResult().toString());

    Assert.assertEquals(99L,
        PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertEquals(0L,
        PublicMethed.queryAccount(contractAddress1, blockingStubFull).getBalance());
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);

    // transfer trx to caller, value enough , function success contractResult(call_value) successed
    param = "\"" + paramValue + "\",\"" + Base58.encode58Check(contractAddress1) + "\"";
    triggerTxid = PublicMethed
        .triggerContract(contractAddress, "testCallVsInsufficientBalance(uint256,address)", param,
            false, 0L, maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);
    logger.info(infoById.get().getReceipt().getResult() + "");

    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    int contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(1, contractResult);

    Assert.assertEquals(infoById.get().getResultValue(), 0);
    Assert.assertEquals(infoById.get().getResult().toString(), "SUCESS");
    Assert.assertEquals(98L,
        PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertEquals(1L,
        PublicMethed.queryAccount(contractAddress1, blockingStubFull).getBalance());
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);

    // transfer trx to caller, value not enough, function success
    // but contractResult(call_value) failed
    param = "\"" + 100 + "\",\"" + Base58.encode58Check(contractAddress1) + "\"";
    triggerTxid = PublicMethed
        .triggerContract(contractAddress, "testCallVsInsufficientBalance(uint256,address)", param,
            false, 0L, maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);
    fee = infoById.get().getFee();
    photonUsed = infoById.get().getReceipt().getPhotonUsage();
    entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    photonFee = infoById.get().getReceipt().getPhotonFee();
    entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    //contractResult`s first boolean value
    contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(0, contractResult);
    Assert.assertEquals(infoById.get().getResultValue(), 0);
    Assert.assertEquals(infoById.get().getResult().toString(), "SUCESS");
    Assert.assertEquals(98L,
        PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertEquals(1L,
        PublicMethed.queryAccount(contractAddress1, blockingStubFull).getBalance());
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);


  }

  @Test(enabled = true, description = "TransferFailed for create")
  public void triggerContract02() {
    Long ContractBalance = PublicMethed.queryAccount(contractAddress, blockingStubFull)
        .getBalance();
    Account info = null;

    AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(accountExcAddress, blockingStubFull);
    info = PublicMethed.queryAccount(accountExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    //Assert.assertTrue(PublicMethed
    //    .sendcoin(contractAddress, 1000100L, accountExcAddress, accountExcKey, blockingStubFull));
    //Assert.assertTrue(PublicMethed
    //    .sendcoin(contractAddress1, 1, accountExcAddress, accountExcKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    logger.info("contractAddress balance before: " + PublicMethed
        .queryAccount(contractAddress, blockingStubFull).getBalance());
    logger.info("callerAddress balance before: " + PublicMethed
        .queryAccount(contractAddress1, blockingStubFull).getBalance());
    long paramValue = 1;
    String param = "\"" + paramValue + "\"";

    String triggerTxid = PublicMethed
        .triggerContract(contractAddress, "testCreateTrxInsufficientBalance(uint256)", param, false,
            0L, maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    logger.info(infoById.get().getReceipt().getResult() + "");

    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    logger.info("contractAddress balance before: " + PublicMethed
        .queryAccount(contractAddress, blockingStubFull).getBalance());
    logger.info("callerAddress balance before: " + PublicMethed
        .queryAccount(contractAddress1, blockingStubFull).getBalance());
    Assert.assertEquals(infoById.get().getResultValue(), 0);
    Assert.assertFalse(infoById.get().getInternalTransactions(0).getRejected());
    Assert.assertEquals(ContractBalance - 1,
        PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);

    param = "\"" + (ContractBalance + 1) + "\"";
    triggerTxid = PublicMethed
        .triggerContract(contractAddress, "testCreateTrxInsufficientBalance(uint256)", param, false,
            0L, maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);
    fee = infoById.get().getFee();
    photonUsed = infoById.get().getReceipt().getPhotonUsage();
    entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    photonFee = infoById.get().getReceipt().getPhotonFee();
    entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    logger.info("contractAddress balance before: " + PublicMethed
        .queryAccount(contractAddress, blockingStubFull).getBalance());
    logger.info("callerAddress balance before: " + PublicMethed
        .queryAccount(contractAddress1, blockingStubFull).getBalance());

    Assert.assertEquals(infoById.get().getResultValue(), 1);
    Assert.assertEquals(infoById.get().getResMessage().toStringUtf8(), "REVERT opcode executed");
    Assert.assertEquals(ContractBalance - 1,
        PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);


  }

  @Test(enabled = true, description = "TransferFailed for create2")
  public void triggerContract03() {
    Long ContractBalance = PublicMethed.queryAccount(contractAddress, blockingStubFull)
        .getBalance();

    Account info;

    AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(accountExcAddress, blockingStubFull);
    info = PublicMethed.queryAccount(accountExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    //Assert.assertTrue(PublicMethed
    //    .sendcoin(contractAddress, 15L, accountExcAddress, accountExcKey, blockingStubFull));
    logger.info("contractAddress balance before: " + PublicMethed
        .queryAccount(contractAddress, blockingStubFull).getBalance());

    String filePath = "./src/test/resources/soliditycode/TransferFailed007.sol";
    String contractName = "Caller";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String testContractCode = retMap.get("byteCode").toString();
    Long salt = 1L;

    String param = "\"" + testContractCode + "\"," + salt;

    String triggerTxid = PublicMethed
        .triggerContract(contractAddress, "deploy(bytes,uint256)", param, false, 0L, maxFeeLimit,
            accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);

    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    Long afterBalance = 0L;
    afterBalance = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    logger.info("contractAddress balance after : " + PublicMethed
        .queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertEquals(0, infoById.get().getResultValue());
    Assert.assertEquals("SUCESS", infoById.get().getResult().toString());
    Assert.assertEquals(ContractBalance - 10L, afterBalance.longValue());
    Assert.assertFalse(infoById.get().getInternalTransactions(0).getRejected());
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);

    triggerTxid = PublicMethed
        .triggerContract(contractAddress, "deploy2(bytes,uint256)", param, false, 0L, maxFeeLimit,
            accountExcAddress, accountExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);

    fee = infoById.get().getFee();
    photonUsed = infoById.get().getReceipt().getPhotonUsage();
    entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    photonFee = infoById.get().getReceipt().getPhotonFee();
    entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    afterBalance = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    logger.info("contractAddress balance after : " + PublicMethed
        .queryAccount(contractAddress, blockingStubFull).getBalance());
    Assert.assertEquals(1, infoById.get().getResultValue());
    Assert.assertEquals("FAILED", infoById.get().getResult().toString());
    Assert.assertEquals(ContractBalance - 10L, afterBalance.longValue());
    Assert.assertEquals(0, ByteArray.toInt(infoById.get().getContractResult(0).toByteArray()));
    Assert.assertTrue(infoById.get().getReceipt().getEntropyUsageTotal() < 10000000);

  }

  @Test(enabled = true, description = "Triggerconstant a transfer function")
  public void triggerContract04() {
    Account account = PublicMethed.queryAccount(accountExcAddress, blockingStubFull);
    Account ContractAccount = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    Long AccountBeforeBalance = account.getBalance();
    Long ContractAccountBalance = ContractAccount.getBalance();

    TransactionExtention return1 = PublicMethed.triggerConstantContractForExtention(contractAddress,
        "testTransferVsInsufficientBalance(uint256)", "1", false, 0L, 1000000000, "0", 0L,
        accountExcAddress, accountExcKey, blockingStubFull);
    Assert.assertEquals(response_code.SUCCESS, return1.getResult().getCode());
    /*Assert.assertEquals(
        "class Program$StaticCallModificationException "
            + ": Attempt to call a state modifying opcode inside STATICCALL",
        return1.getResult().getMessage().toStringUtf8());*/

    logger.info("return1: " + return1);

    account = PublicMethed.queryAccount(accountExcAddress, blockingStubFull);
    ContractAccount = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    Assert.assertEquals(AccountBeforeBalance.longValue(), account.getBalance());
    Assert.assertEquals(ContractAccountBalance.longValue(), ContractAccount.getBalance());
  }

  /**
   * constructor.
   */
  @AfterClass

  public void shutdown() throws InterruptedException {
    PublicMethed
        .freedResource(accountExcAddress, accountExcKey, testNetAccountAddress, blockingStubFull);
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
