package stest.vision.wallet.dailybuild.delaytransaction;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class DelayTransaction005 {

  private static final long now = System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  Long delaySecond = 10L;
  ByteString assetId;
  SmartContract smartContract;
  ECKey ecKey = new ECKey(Utils.getRandom());
  byte[] smartContractOwnerAddress = ecKey.getAddress();
  String smartContractOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
  private Long delayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.delayTransactionFee");
  private Long cancleDelayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.cancleDelayTransactionFee");
  private byte[] contractAddress = null;

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = false)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = false, description = "Delay update entropy limit contract")
  public void test1DelayUpdateSetting() {
    //get account
    ecKey = new ECKey(Utils.getRandom());
    smartContractOwnerAddress = ecKey.getAddress();
    smartContractOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    PublicMethed.printAddress(smartContractOwnerKey);

    Assert.assertTrue(PublicMethed.sendcoin(smartContractOwnerAddress, 2048000000, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.freezeBalance(smartContractOwnerAddress, 10000000L, 3,
        smartContractOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String contractName = "VISIONTOKEN";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_ContractScenario004_deployErc20VisionToken");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_ContractScenario004_deployErc20VisionToken");
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, 999L, "0", 0, null,
        smartContractOwnerKey, smartContractOwnerAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Long oldOriginEntropyLimit = 567L;
    Assert.assertTrue(PublicMethed.updateEntropyLimit(contractAddress, oldOriginEntropyLimit,
        smartContractOwnerKey, smartContractOwnerAddress, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getOriginEntropyLimit() == oldOriginEntropyLimit);

    Long newOriginEntropyLimit = 8765L;
    final String txid = PublicMethed.updateEntropyLimitDelayGetTxid(contractAddress,
        newOriginEntropyLimit, delaySecond, smartContractOwnerKey, smartContractOwnerAddress,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getOriginEntropyLimit() == oldOriginEntropyLimit);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    logger.info("newOriginEntropyLimit: " + smartContract.getOriginEntropyLimit());
    Assert.assertTrue(smartContract.getOriginEntropyLimit() == newOriginEntropyLimit);

    Long photonFee = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get().getReceipt()
        .getPhotonFee();
    Long fee = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get().getFee();
    Assert.assertTrue(fee - photonFee == delayTransactionFee);

  }

  @Test(enabled = false, description = "Cancel delay entropy limit contract")
  public void test2CancelDelayUpdateSetting() {
    //get account
    final Long oldOriginEntropyLimit = smartContract.getOriginEntropyLimit();
    final Long newOriginEntropyLimit = 466L;

    String txid = PublicMethed.updateEntropyLimitDelayGetTxid(contractAddress, newOriginEntropyLimit,
        delaySecond, smartContractOwnerKey, smartContractOwnerAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account ownerAccount = PublicMethed.queryAccount(smartContractOwnerKey, blockingStubFull);
    final Long beforeCancelBalance = ownerAccount.getBalance();

    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid, fromAddress, testKey002,
        blockingStubFull));
    final String cancelTxid = PublicMethed.cancelDeferredTransactionByIdGetTxid(txid,
        smartContractOwnerAddress, smartContractOwnerKey, blockingStubFull);
    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid, smartContractOwnerAddress,
        smartContractOwnerKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    logger.info("newOriginEntropyLimit: " + smartContract.getOriginEntropyLimit());
    Assert.assertTrue(smartContract.getOriginEntropyLimit() == oldOriginEntropyLimit);

    final Long photonFee = PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull).get()
        .getReceipt().getPhotonFee();
    final Long fee = PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull).get()
        .getFee();
    logger.info("photon fee : " + PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull)
        .get().getReceipt().getPhotonFee());
    logger.info("Fee : " + PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull)
        .get().getFee());

    ownerAccount = PublicMethed.queryAccount(smartContractOwnerKey, blockingStubFull);
    Long afterCancelBalance = ownerAccount.getBalance();
    Assert.assertTrue(fee - photonFee == cancleDelayTransactionFee);
    Assert.assertTrue(fee == beforeCancelBalance - afterCancelBalance);

    logger.info("beforeCancelBalance: " + beforeCancelBalance);
    logger.info("afterCancelBalance : " + afterCancelBalance);
  }

  /**
   * constructor.
   */

  @AfterClass(enabled = false)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


