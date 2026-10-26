<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>My Room</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="display: flex">
        <div style="margin-top: 55px">
          <div style="margin-left: 50px;margin-top: 20px">
            <!--      房间信息-->
            <el-descriptions :column="1" border style="width: 500px" title="Rooms">
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <user/>
                    </el-icon> Building No. </div>
                </template>
                <span class="rightSpan">{{ this.room.dormBuildId }}</span>
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <location/>
                    </el-icon> Room No. </div>
                </template>
                <span class="rightSpan">{{ this.room.dormRoomId }}</span>
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <tickets/>
                    </el-icon> Floor </div>
                </template>
                <span class="rightSpan">{{ this.room.floorNum }}</span>
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <office-building/>
                    </el-icon> Capacity </div>
                </template>
                <span class="rightSpan">{{ this.room.maxCapacity }}</span>
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <iphone/>
                    </el-icon> Occupancy </div>
                </template>
                <span class="rightSpan">{{ this.room.currentCapacity }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <!--      床位信息-->
          <div style="margin-left: 50px;margin-top: 40px">
            <el-descriptions :column="1" border style="width: 500px" title="Beds">
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <user/>
                    </el-icon> Bed 1 </div>
                </template>
                <el-tag
                    v-if="this.room.firstBed != null"
                    :type="this.name === this.room.firstBed ? 'primary':'info'"
                    disable-transitions
                >{{ this.room.firstBed }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <location/>
                    </el-icon> Bed 2 </div>
                </template>
                <el-tag
                    v-if="this.room.secondBed != null"
                    :type="this.name === this.room.secondBed ? 'primary':'info'"
                    disable-transitions
                >{{ this.room.secondBed }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <tickets/>
                    </el-icon> Bed 3 </div>
                </template>
                <el-tag
                    v-if="this.room.thirdBed != null"
                    :type="this.name === this.room.thirdBed ? 'primary':'info'"
                    disable-transitions
                >{{ this.room.thirdBed }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div>
                    <el-icon>
                      <office-building/>
                    </el-icon> Bed 4 </div>
                </template>
                <el-tag
                    v-if="this.room.fourthBed != null"
                    :type="this.name === this.room.fourthBed ? 'primary':'info'"
                    disable-transitions
                >{{ this.room.fourthBed }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
        <div style="margin-left: 100px;margin-top: 85px">
          <img alt="" src="../../public/myRoom.png" style="width: 600px">
        </div>
      </div>
    </el-card>
  </div>
</template>
<script src="@/assets/js/MyRoomInfo.js"></script>
<style scoped>@import '../assets/css/MyRoomInfo.css';</style>