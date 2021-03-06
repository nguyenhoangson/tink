// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

#include "cc/registry.h"
#include "cc/mac/mac_config.h"
#include "cc/util/status.h"
#include "gtest/gtest.h"

namespace util = crypto::tink::util;

namespace crypto {
namespace tink {
namespace {

class MacConfigTest : public ::testing::Test {
};

TEST_F(MacConfigTest, testBasic) {
  // Registration of standard key types works.
  auto& registry = Registry::get_default_registry();
  std::string key_type =  "type.googleapis.com/google.crypto.tink.HmacKey";
  auto manager_result = registry.get_key_manager<Mac>(key_type);
  EXPECT_FALSE(manager_result.ok());
  EXPECT_EQ(util::error::NOT_FOUND, manager_result.status().error_code());
  EXPECT_TRUE(MacConfig::RegisterStandardKeyTypes().ok());
  manager_result = registry.get_key_manager<Mac>(key_type);
  EXPECT_TRUE(manager_result.ok()) << manager_result.status();
  EXPECT_TRUE(manager_result.ValueOrDie()->DoesSupport(key_type));

  // Registration of legacy key types works.
  EXPECT_TRUE(MacConfig::RegisterLegacyKeyTypes().ok());

  // Registration of individual key managers checks the passed pointer.
  auto status = MacConfig::RegisterKeyManager(nullptr);
  EXPECT_FALSE(status.ok());
}


}  // namespace
}  // namespace tink
}  // namespace crypto


int main(int ac, char* av[]) {
  testing::InitGoogleTest(&ac, av);
  return RUN_ALL_TESTS();
}
