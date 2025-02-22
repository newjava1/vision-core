package stest.vision.wallet.dailybuild.manual;

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
import org.vision.api.WalletSolidityGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class WalletTestAccount014 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] account014Address = ecKey1.getAddress();
  String account014Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] account014SecondAddress = ecKey2.getAddress();
  String account014SecondKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private ManagedChannel channelSolidity = null;
  private ManagedChannel channelSoliInFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSoliInFull = null;
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  private String soliInFullnode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(1);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(testKey002);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);

    channelSoliInFull = ManagedChannelBuilder.forTarget(soliInFullnode)
        .usePlaintext(true)
        .build();
    blockingStubSoliInFull = WalletSolidityGrpc.newBlockingStub(channelSoliInFull);
  }

  @Test(enabled = true, description = "Query freePhotonUsage in 50061")
  public void fullAndSoliMerged1ForFreePhotonUsage() {
    //Create account014
    ecKey1 = new ECKey(Utils.getRandom());
    account014Address = ecKey1.getAddress();
    account014Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    ecKey2 = new ECKey(Utils.getRandom());
    account014SecondAddress = ecKey2.getAddress();
    account014SecondKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

    PublicMethed.printAddress(account014Key);
    PublicMethed.printAddress(account014SecondKey);
    Assert.assertTrue(PublicMethed.sendcoin(account014Address, 1000000000L, fromAddress,
        testKey002, blockingStubFull));

    //Test freePhotonUsage in fullnode and soliditynode.
    Assert.assertTrue(PublicMethed.sendcoin(account014SecondAddress, 5000000L,
        account014Address, account014Key,
        blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(account014SecondAddress, 5000000L,
        account014Address, account014Key,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account account014 = PublicMethed.queryAccount(account014Address, blockingStubFull);
    final long freePhotonUsageInFullnode = account014.getFreePhotonUsage();
    final long createTimeInFullnode = account014.getCreateTime();
    final long lastOperationTimeInFullnode = account014.getLatestOprationTime();
    final long lastCustomeFreeTimeInFullnode = account014.getLatestConsumeFreeTime();
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSoliInFull);
    account014 = PublicMethed.queryAccount(account014Address, blockingStubSoliInFull);
    final long freePhotonUsageInSoliInFull = account014.getFreePhotonUsage();
    final long createTimeInSoliInFull = account014.getCreateTime();
    final long lastOperationTimeInSoliInFull = account014.getLatestOprationTime();
    final long lastCustomeFreeTimeInSoliInFull = account014.getLatestConsumeFreeTime();
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    account014 = PublicMethed.queryAccount(account014Address, blockingStubSolidity);
    final long freePhotonUsageInSolidity = account014.getFreePhotonUsage();
    final long createTimeInSolidity = account014.getCreateTime();
    final long lastOperationTimeInSolidity = account014.getLatestOprationTime();
    final long lastCustomeFreeTimeInSolidity = account014.getLatestConsumeFreeTime();
    Assert.assertTrue(freePhotonUsageInSoliInFull > 0 && freePhotonUsageInSolidity > 0
        && freePhotonUsageInFullnode > 0);
    Assert.assertTrue(freePhotonUsageInFullnode <= freePhotonUsageInSoliInFull + 5
        && freePhotonUsageInFullnode >= freePhotonUsageInSoliInFull - 5);
    Assert.assertTrue(freePhotonUsageInFullnode <= freePhotonUsageInSolidity + 5
        && freePhotonUsageInFullnode >= freePhotonUsageInSolidity - 5);
    Assert.assertTrue(createTimeInFullnode == createTimeInSolidity && createTimeInFullnode
        == createTimeInSoliInFull);
    Assert.assertTrue(createTimeInSoliInFull != 0);
    Assert.assertTrue(lastOperationTimeInFullnode == lastOperationTimeInSolidity
        && lastOperationTimeInFullnode == lastOperationTimeInSoliInFull);
    Assert.assertTrue(lastOperationTimeInSoliInFull != 0);
    Assert.assertTrue(lastCustomeFreeTimeInFullnode == lastCustomeFreeTimeInSolidity
        && lastCustomeFreeTimeInFullnode == lastCustomeFreeTimeInSoliInFull);
    Assert.assertTrue(lastCustomeFreeTimeInSoliInFull != 0);
  }

  @Test(enabled = true, description = "Query Photon usage in 50061")
  public void fullAndSoliMerged2ForPhotonUsage() {

    Assert.assertTrue(PublicMethed.freezeBalance(account014Address, 1000000L, 3,
        account014Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(account014SecondAddress, 1000000L,
        account014Address, account014Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(account014Address, 1000000,
        3, 1, account014Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(account014Address, 1000000,
        3, 0, ByteString.copyFrom(
            account014SecondAddress), account014Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(account014Address, 1000000,
        3, 1, ByteString.copyFrom(
            account014SecondAddress), account014Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(account014SecondAddress, 1000000,
        3, 0, ByteString.copyFrom(
            account014Address), account014SecondKey, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(account014SecondAddress, 1000000,
        3, 1, ByteString.copyFrom(
            account014Address), account014SecondKey, blockingStubFull));

    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSoliInFull);
    Account account014 = PublicMethed.queryAccount(account014Address, blockingStubFull);
    final long lastCustomeTimeInFullnode = account014.getLatestConsumeTime();
    final long photonUsageInFullnode = account014.getPhotonUsage();
    final long acquiredForPhotonInFullnode = account014
        .getAcquiredDelegatedFrozenBalanceForPhoton();
    final long delegatedPhotonInFullnode = account014.getDelegatedFrozenBalanceForPhoton();
    final long acquiredForEntropyInFullnode = account014
        .getAccountResource().getAcquiredDelegatedFrozenBalanceForEntropy();
    final long delegatedForEntropyInFullnode = account014
        .getAccountResource().getDelegatedFrozenBalanceForEntropy();
    logger.info("delegatedForEntropyInFullnode " + delegatedForEntropyInFullnode);
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSoliInFull);
    account014 = PublicMethed.queryAccount(account014Address, blockingStubSoliInFull);
    final long lastCustomeTimeInSoliInFull = account014.getLatestConsumeTime();
    logger.info("freePhotonUsageInSoliInFull " + lastCustomeTimeInSoliInFull);
    final long photonUsageInSoliInFull = account014.getPhotonUsage();
    final long acquiredForPhotonInSoliInFull = account014
        .getAcquiredDelegatedFrozenBalanceForPhoton();
    final long delegatedPhotonInSoliInFull = account014.getDelegatedFrozenBalanceForPhoton();
    final long acquiredForEntropyInSoliInFull = account014
        .getAccountResource().getAcquiredDelegatedFrozenBalanceForEntropy();
    final long delegatedForEntropyInSoliInFull = account014
        .getAccountResource().getDelegatedFrozenBalanceForEntropy();
    logger.info("delegatedForEntropyInSoliInFull " + delegatedForEntropyInSoliInFull);
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    account014 = PublicMethed.queryAccount(account014Address, blockingStubSolidity);
    final long photonUsageInSolidity = account014.getPhotonUsage();
    final long lastCustomeTimeInSolidity = account014.getLatestConsumeTime();
    final long acquiredForPhotonInSolidity = account014
        .getAcquiredDelegatedFrozenBalanceForPhoton();
    final long delegatedPhotonInSolidity = account014.getDelegatedFrozenBalanceForPhoton();
    final long acquiredForEntropyInSolidity = account014.getAccountResource()
        .getAcquiredDelegatedFrozenBalanceForEntropy();
    final long delegatedForEntropyInSolidity = account014.getAccountResource()
        .getDelegatedFrozenBalanceForEntropy();

    logger.info("delegatedForEntropyInSolidity " + delegatedForEntropyInSolidity);
    Assert.assertTrue(photonUsageInSoliInFull > 0 && photonUsageInSolidity > 0
        && photonUsageInFullnode > 0);
    Assert.assertTrue(photonUsageInFullnode <= photonUsageInSoliInFull + 5
        && photonUsageInFullnode >= photonUsageInSoliInFull - 5);
    Assert.assertTrue(photonUsageInFullnode <= photonUsageInSolidity + 5
        && photonUsageInFullnode >= photonUsageInSolidity - 5);
    Assert.assertTrue(acquiredForPhotonInFullnode == acquiredForPhotonInSoliInFull
        && acquiredForPhotonInFullnode == acquiredForPhotonInSolidity);
    Assert.assertTrue(delegatedPhotonInFullnode == delegatedPhotonInSoliInFull
        && delegatedPhotonInFullnode == delegatedPhotonInSolidity);
    Assert.assertTrue(acquiredForEntropyInFullnode == acquiredForEntropyInSoliInFull
        && acquiredForEntropyInFullnode == acquiredForEntropyInSolidity);
    Assert.assertTrue(delegatedForEntropyInFullnode == delegatedForEntropyInSoliInFull
        && delegatedForEntropyInFullnode == delegatedForEntropyInSolidity);
    Assert.assertTrue(acquiredForPhotonInSoliInFull == 1000000
        && delegatedPhotonInSoliInFull == 1000000 && acquiredForEntropyInSoliInFull == 1000000
        && delegatedForEntropyInSoliInFull == 1000000);
    logger.info("lastCustomeTimeInSoliInFull " + lastCustomeTimeInSoliInFull);
    Assert.assertTrue(lastCustomeTimeInFullnode == lastCustomeTimeInSolidity
        && lastCustomeTimeInFullnode == lastCustomeTimeInSoliInFull);
    logger.info("lastCustomeTimeInSoliInFull " + lastCustomeTimeInSoliInFull);
    Assert.assertTrue(lastCustomeTimeInSoliInFull != 0);

  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(account014Address, account014Key, fromAddress, blockingStubFull);
    PublicMethed
        .freedResource(account014SecondAddress, account014SecondKey, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}