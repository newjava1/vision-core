package stest.vision.wallet.contract.scenario;

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
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.TransactionInfo;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractScenario004 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract004Address = ecKey1.getAddress();
  String contract004Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

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
    PublicMethed.printAddress(contract004Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true)
  public void deployErc20VisionTokenWithoutData() {
    Assert.assertTrue(PublicMethed.sendcoin(contract004Address, 200000000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(contract004Address, 100000000L,
        3, 1, contract004Key, blockingStubFull));
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract004Address,
        blockingStubFull);
    Long entropyLimit = accountResource.getEntropyLimit();
    Long entropyUsage = accountResource.getEntropyUsed();

    logger.info("before entropy limit is " + Long.toString(entropyLimit));
    logger.info("before entropy usage is " + Long.toString(entropyUsage));

    String filePath = "./src/test/resources/soliditycode//contractScenario004.sol";
    String contractName = "VisionToken";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            0L, 100, null, contract004Key, contract004Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    System.out.println(info);
    Assert.assertTrue(info.get().getResultValue() == 1);
  }

  @Test(enabled = true)
  public void deployErc20VisionTokenWithData() {
    Assert.assertTrue(PublicMethed
        .sendcoin(contract004Address, 200000000L, fromAddress, testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(contract004Address, 100000000L,
        3, 1, contract004Key, blockingStubFull));
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract004Address,
        blockingStubFull);
    Long entropyLimit = accountResource.getEntropyLimit();
    Long entropyUsage = accountResource.getEntropyUsed();

    logger.info("before entropy limit is " + Long.toString(entropyLimit));
    logger.info("before entropy usage is " + Long.toString(entropyUsage));

    String filePath = "./src/test/resources/soliditycode//contractScenario004.sol";
    String contractName = "VisionToken";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String constructorStr = "constructor(address)";
    String data = "\"" + Base58.encode58Check(contract004Address) + "\"";
    String txid = PublicMethed
        .deployContractWithConstantParame(contractName, abi, code, constructorStr, data, "",
            maxFeeLimit, 0L, 100, null, contract004Key, contract004Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    System.out.println(info);
    Assert.assertTrue(info.get().getResultValue() == 0);
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


