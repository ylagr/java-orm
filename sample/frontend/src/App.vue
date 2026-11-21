<template>
  <el-container class="layout-container">
    <el-aside width="220px" style="background-color: #1e222d;">
      <div class="logo">
        <span style="color: #409eff;">⚡</span> JavaORM 商城后台
      </div>
      <el-menu
        :default-active="activeMenu"
        class="aside-menu"
        text-color="#c0c4cc"
        active-text-color="#ffffff"
        @select="handleMenuSelect">
        <el-menu-item index="dashboard">
          <el-icon><i-ep-data-analysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="product">
          <el-icon><i-ep-goods /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="order">
          <el-icon><i-ep-list /></el-icon>
          <span>订单管理</span>
          <span class="badge-auto-include">AutoInclude</span>
        </el-menu-item>
        <el-menu-item index="category">
          <el-icon><i-ep-folder /></el-icon>
          <span>商品分类</span>
        </el-menu-item>
        <el-menu-item index="user">
          <el-icon><i-ep-user /></el-icon>
          <span>会员管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header-bar">
        <div style="font-size: 15px; font-weight: 500; color: #595959;">
          MyBatis-Plus 兼容模式 &amp; 零反射自动包含装配演示
        </div>
        <div style="display: flex; align-items: center; gap: 15px;">
          <el-tag type="success" effect="dark">Spring Boot + JavaORM</el-tag>
          <el-avatar size="small" src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png" />
          <span style="font-size: 14px; color: #606266;">超级管理员</span>
        </div>
      </el-header>

      <el-main class="main-content">
        <!-- 1. 数据看板 -->
        <div v-if="activeMenu === 'dashboard'">
          <el-row :gutter="20" style="margin-bottom: 20px;">
            <el-col :span="6">
              <el-card class="card-stat" shadow="hover">
                <div class="stat-title">总销售额 (已支付)</div>
                <div class="stat-value" style="color: #67c23a;">¥ {{ stats.totalSales ? Number(stats.totalSales).toFixed(2) : '0.00' }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="card-stat" shadow="hover">
                <div class="stat-title">订单总数</div>
                <div class="stat-value" style="color: #409eff;">{{ stats.totalOrders || 0 }} 笔</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="card-stat" shadow="hover">
                <div class="stat-title">在售商品数</div>
                <div class="stat-value" style="color: #e6a23c;">{{ stats.totalProducts || 0 }} 款</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="card-stat" shadow="hover">
                <div class="stat-title">注册会员总数</div>
                <div class="stat-value" style="color: #909399;">{{ stats.totalUsers || 0 }} 人</div>
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-card class="table-card" header="商品分类分布统计 (SQL聚合)">
                <div v-for="c in (stats.categoryDist || [])" :key="c.name" style="margin-bottom: 15px;">
                  <div style="display: flex; justify-content: space-between; margin-bottom: 5px; font-size: 14px;">
                    <span>{{ c.name }}</span>
                    <span>{{ c.value }} 件</span>
                  </div>
                  <el-progress :percentage="calcPercentage(c.value, stats.totalProducts)" status="success" />
                </div>
              </el-card>
            </el-col>

            <el-col :span="12">
              <el-card class="table-card" header="热销商品 TOP 5 (JavaORM Limit)">
                <el-table :data="stats.topProducts || []" stripe style="width: 100%">
                  <el-table-column type="index" label="排名" width="60" />
                  <el-table-column prop="name" label="商品名称" />
                  <el-table-column prop="price" label="售价" width="100">
                    <template #default="scope">¥{{ scope.row.price }}</template>
                  </el-table-column>
                  <el-table-column prop="sales" label="销量" width="100">
                    <template #default="scope">
                      <el-tag type="danger" size="small">{{ scope.row.sales }} 件</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <!-- 2. 商品管理 -->
        <div v-if="activeMenu === 'product'">
          <el-card class="table-card">
            <el-row :gutter="15" class="search-bar">
              <el-col :span="6">
                <el-input v-model="productQuery.keyword" placeholder="商品名称关键词" clearable @clear="loadProducts" />
              </el-col>
              <el-col :span="5">
                <el-select v-model="productQuery.categoryId" placeholder="全部分类" clearable @change="loadProducts" style="width: 100%">
                  <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
                </el-select>
              </el-col>
              <el-col :span="4">
                <el-select v-model="productQuery.status" placeholder="上架状态" clearable @change="loadProducts" style="width: 100%">
                  <el-option label="已上架" :value="1" />
                  <el-option label="已下架" :value="0" />
                </el-select>
              </el-col>
              <el-col :span="9" style="text-align: right;">
                <el-button type="primary" @click="loadProducts">查询</el-button>
                <el-button type="success" @click="openProductDialog()">新增商品</el-button>
              </el-col>
            </el-row>

            <el-table :data="productList" v-loading="productLoading" border stripe style="width: 100%;">
              <el-table-column prop="id" label="ID" width="70" align="center" />
              <el-table-column label="图片" width="80" align="center">
                <template #default="scope">
                  <img :src="scope.row.image" class="product-thumb" alt="商品主图">
                </template>
              </el-table-column>
              <el-table-column prop="name" label="商品名称" min-width="160" />
              <el-table-column prop="categoryName" label="分类" width="110" align="center">
                <template #default="scope">
                  <el-tag size="small">{{ scope.row.categoryName }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="price" label="售价" width="110" align="right">
                <template #default="scope">¥{{ Number(scope.row.price).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="stock" label="库存" width="90" align="center" />
              <el-table-column prop="sales" label="销量" width="90" align="center" />
              <el-table-column prop="version" label="乐观锁版本" width="100" align="center">
                <template #default="scope">
                  <el-tag type="info" size="small">v{{ scope.row.version }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="上架状态" width="100" align="center">
                <template #default="scope">
                  <el-switch
                    :model-value="scope.row.status === 1"
                    @change="(val) => handleToggleProductStatus(scope.row, val)" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center">
                <template #default="scope">
                  <el-button size="small" type="primary" link @click="openProductDialog(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" link @click="handleDeleteProduct(scope.row)">逻辑删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div style="display: flex; justify-content: flex-end; margin-top: 15px;">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :current-page="productQuery.page"
                :page-size="productQuery.size"
                :total="productTotal"
                @current-change="handleProductPageChange" />
            </div>
          </el-card>
        </div>

        <!-- 3. 订单管理 -->
        <div v-if="activeMenu === 'order'">
          <el-card class="table-card">
            <el-row :gutter="15" class="search-bar">
              <el-col :span="6">
                <el-input v-model="orderQuery.orderNo" placeholder="订单编号搜索" clearable @clear="loadOrders" />
              </el-col>
              <el-col :span="5">
                <el-select v-model="orderQuery.status" placeholder="全部状态" clearable @change="loadOrders" style="width: 100%">
                  <el-option label="待付款" :value="10" />
                  <el-option label="待发货" :value="20" />
                  <el-option label="已发货" :value="30" />
                  <el-option label="已完成" :value="40" />
                  <el-option label="已取消" :value="50" />
                </el-select>
              </el-col>
              <el-col :span="13" style="text-align: right;">
                <el-button type="primary" @click="loadOrders">查询订单</el-button>
              </el-col>
            </el-row>

            <el-table :data="orderList" v-loading="orderLoading" border stripe style="width: 100%;">
              <el-table-column prop="id" label="ID" width="70" align="center" />
              <el-table-column prop="orderNo" label="订单编号" min-width="160" />
              <el-table-column label="下单会员 (AutoInclude)" min-width="140">
                <template #default="scope">
                  <div v-if="scope.row.user">
                    <span style="font-weight: 500;">{{ scope.row.user.nickname || scope.row.user.username }}</span>
                    <br><span style="font-size: 12px; color: #909399;">{{ scope.row.user.phone }}</span>
                  </div>
                  <span v-else style="color: #909399;">未知用户</span>
                </template>
              </el-table-column>
              <el-table-column label="实付金额" width="110" align="right">
                <template #default="scope">
                  <span style="color: #f56c6c; font-weight: bold;">¥{{ Number(scope.row.payAmount).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="包含明细数" width="110" align="center">
                <template #default="scope">
                  <el-tag type="info" size="small">{{ (scope.row.items || []).length }} 种商品</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="scope">
                  <el-tag :type="getOrderStatusTag(scope.row.status)">
                    {{ getOrderStatusText(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="下单时间" width="170" align="center">
                <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="180" align="center">
                <template #default="scope">
                  <el-button size="small" type="primary" link @click="showOrderDetail(scope.row)">详情与明细</el-button>
                  <el-button v-if="scope.row.status === 20" size="small" type="success" link @click="handleShipOrder(scope.row)">发货</el-button>
                  <el-button v-if="scope.row.status === 10 || scope.row.status === 20" size="small" type="danger" link @click="handleCancelOrder(scope.row)">取消</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div style="display: flex; justify-content: flex-end; margin-top: 15px;">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :current-page="orderQuery.page"
                :page-size="orderQuery.size"
                :total="orderTotal"
                @current-change="handleOrderPageChange" />
            </div>
          </el-card>
        </div>

        <!-- 4. 商品分类管理 -->
        <div v-if="activeMenu === 'category'">
          <el-card class="table-card">
            <div style="display: flex; justify-content: space-between; margin-bottom: 15px;">
              <div style="font-size: 15px; font-weight: bold;">商品分类列表</div>
              <el-button type="success" size="small" @click="openCategoryDialog()">新增分类</el-button>
            </div>
            <el-table :data="categories" border stripe style="width: 100%;">
              <el-table-column prop="id" label="ID" width="80" align="center" />
              <el-table-column prop="name" label="分类名称" min-width="150" />
              <el-table-column prop="sort" label="排序权重" width="100" align="center" />
              <el-table-column prop="createTime" label="创建时间" width="180" align="center">
                <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center">
                <template #default="scope">
                  <el-button size="small" type="primary" link @click="openCategoryDialog(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" link @click="handleDeleteCategory(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <!-- 5. 会员管理 -->
        <div v-if="activeMenu === 'user'">
          <el-card class="table-card">
            <el-row :gutter="15" class="search-bar">
              <el-col :span="6">
                <el-input v-model="userQuery.keyword" placeholder="搜索用户名 / 手机号" clearable @clear="loadUsers" />
              </el-col>
              <el-col :span="18" style="text-align: right;">
                <el-button type="primary" @click="loadUsers">查询会员</el-button>
              </el-col>
            </el-row>

            <el-table :data="userList" v-loading="userLoading" border stripe style="width: 100%;">
              <el-table-column prop="id" label="ID" width="70" align="center" />
              <el-table-column prop="username" label="用户名" width="120" />
              <el-table-column prop="nickname" label="昵称" width="120" />
              <el-table-column prop="phone" label="手机号码" width="130" align="center" />
              <el-table-column prop="points" label="可用积分" width="100" align="right" />
              <el-table-column prop="balance" label="账户余额" width="120" align="right">
                <template #default="scope">¥{{ Number(scope.row.balance).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="账号状态" width="100" align="center">
                <template #default="scope">
                  <el-switch
                    :model-value="scope.row.status === 1"
                    @change="(val) => handleToggleUserStatus(scope.row, val)" />
                </template>
              </el-table-column>
            </el-table>

            <div style="display: flex; justify-content: flex-end; margin-top: 15px;">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :current-page="userQuery.page"
                :page-size="userQuery.size"
                :total="userTotal"
                @current-change="handleUserPageChange" />
            </div>
          </el-card>
        </div>
      </el-main>
    </el-container>
  </el-container>

  <!-- 订单明细弹窗 -->
  <el-dialog v-model="orderDetailVisible" title="订单全貌 (selectAutoInclude 自动装配结果)" width="680px">
    <div v-if="currentOrder">
      <el-descriptions title="订单基础信息" :column="2" border style="margin-bottom: 20px;">
        <el-descriptions-item label="订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="getOrderStatusTag(currentOrder.status)">{{ getOrderStatusText(currentOrder.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="收货人">{{ currentOrder.receiverName }} ({{ currentOrder.receiverPhone }})</el-descriptions-item>
        <el-descriptions-item label="收货地址">{{ currentOrder.receiverAddress }}</el-descriptions-item>
        <el-descriptions-item label="下单会员">
          {{ currentOrder.user ? (currentOrder.user.nickname + ' / ' + currentOrder.user.username) : '未指定' }}
        </el-descriptions-item>
        <el-descriptions-item label="实付总额">
          <span style="color: #f56c6c; font-weight: bold;">¥{{ Number(currentOrder.payAmount).toFixed(2) }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <div style="font-size: 14px; font-weight: bold; margin-bottom: 10px;">包含的商品明细 (List&lt;MallOrderItemDto&gt;)</div>
      <el-table :data="currentOrder.items || []" border size="small">
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column prop="price" label="单价" width="100" align="right">
          <template #default="scope">¥{{ Number(scope.row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column prop="totalAmount" label="小计" width="100" align="right">
          <template #default="scope">¥{{ Number(scope.row.totalAmount).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>

  <!-- 商品对话框 -->
  <el-dialog v-model="productDialogVisible" :title="productForm.id ? '编辑商品' : '新增商品'" width="550px">
    <el-form :model="productForm" label-width="100px">
      <el-form-item label="商品名称" required>
        <el-input v-model="productForm.name" />
      </el-form-item>
      <el-form-item label="副标题/卖点">
        <el-input v-model="productForm.title" />
      </el-form-item>
      <el-form-item label="所属分类" required>
        <el-select v-model="productForm.categoryId" placeholder="请选择分类" style="width: 100%;">
          <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="销售价格" required>
        <el-input-number v-model="productForm.price" :precision="2" :step="10" :min="0" style="width: 100%;" />
      </el-form-item>
      <el-form-item label="库存数量" required>
        <el-input-number v-model="productForm.stock" :min="0" style="width: 100%;" />
      </el-form-item>
      <el-form-item label="主图 URL">
        <el-input v-model="productForm.image" />
      </el-form-item>
      <el-form-item v-if="productForm.id" label="乐观锁版本">
        <el-tag type="info">当前版本: v{{ productForm.version }} (更新时将自动验证 @Version 并递增)</el-tag>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="productDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSaveProduct">保存</el-button>
    </template>
  </el-dialog>

  <!-- 分类对话框 -->
  <el-dialog v-model="categoryDialogVisible" :title="categoryForm.id ? '编辑分类' : '新增分类'" width="450px">
    <el-form :model="categoryForm" label-width="80px">
      <el-form-item label="分类名称" required>
        <el-input v-model="categoryForm.name" />
      </el-form-item>
      <el-form-item label="排序权重">
        <el-input-number v-model="categoryForm.sort" :min="0" style="width: 100%;" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="categoryDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSaveCategory">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const activeMenu = ref('dashboard')
const stats = ref({})
const categories = ref([])

const productList = ref([])
const productTotal = ref(0)
const productLoading = ref(false)
const productQuery = reactive({ keyword: '', categoryId: null, status: null, page: 1, size: 10 })
const productDialogVisible = ref(false)
const productForm = reactive({ id: null, name: '', title: '', categoryId: null, price: 99, stock: 100, image: '', version: 1 })

const orderList = ref([])
const orderTotal = ref(0)
const orderLoading = ref(false)
const orderQuery = reactive({ orderNo: '', status: null, page: 1, size: 10 })
const orderDetailVisible = ref(false)
const currentOrder = ref(null)

const categoryDialogVisible = ref(false)
const categoryForm = reactive({ id: null, name: '', sort: 1 })

const userList = ref([])
const userTotal = ref(0)
const userLoading = ref(false)
const userQuery = reactive({ keyword: '', status: null, page: 1, size: 10 })

const api = axios.create({ baseURL: '/api' })

const loadDashboard = async () => {
  try {
    const res = await api.get('/dashboard/stats')
    if (res.data.code === 200) stats.value = res.data.data
  } catch (e) {
    console.error(e)
  }
}

const loadCategories = async () => {
  try {
    const res = await api.get('/categories')
    if (res.data.code === 200) categories.value = res.data.data
  } catch (e) {
    console.error(e)
  }
}

const loadProducts = async () => {
  productLoading.value = true
  try {
    const res = await api.get('/products', { params: productQuery })
    if (res.data.code === 200) {
      productList.value = res.data.data.records || []
      productTotal.value = res.data.total || 0
    }
  } finally {
    productLoading.value = false
  }
}

const loadOrders = async () => {
  orderLoading.value = true
  try {
    const res = await api.get('/orders', { params: orderQuery })
    if (res.data.code === 200) {
      orderList.value = res.data.data.records || []
      orderTotal.value = res.data.total || 0
    }
  } finally {
    orderLoading.value = false
  }
}

const loadUsers = async () => {
  userLoading.value = true
  try {
    const res = await api.get('/users', { params: userQuery })
    if (res.data.code === 200) {
      userList.value = res.data.data.records || []
      userTotal.value = res.data.total || 0
    }
  } finally {
    userLoading.value = false
  }
}

const handleMenuSelect = (index) => {
  activeMenu.value = index
  if (index === 'dashboard') loadDashboard()
  if (index === 'product') { loadCategories(); loadProducts() }
  if (index === 'order') loadOrders()
  if (index === 'category') loadCategories()
  if (index === 'user') loadUsers()
}

const handleToggleProductStatus = async (row, val) => {
  const nextStatus = val ? 1 : 0
  await api.put(`/products/${row.id}/status?status=${nextStatus}`)
  row.status = nextStatus
  ElMessage.success(val ? '商品已上架' : '商品已下架')
}

const openProductDialog = (row) => {
  if (row) {
    Object.assign(productForm, row)
  } else {
    Object.assign(productForm, { id: null, name: '', title: '', categoryId: categories.value[0]?.id || 1, price: 99, stock: 100, image: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=300', version: 1 })
  }
  productDialogVisible.value = true
}

const handleSaveProduct = async () => {
  if (!productForm.name) {
    ElMessage.warning('请输入商品名称')
    return
  }
  try {
    if (productForm.id) {
      await api.put(`/products/${productForm.id}`, productForm)
      ElMessage.success('商品更新成功（乐观锁版本已递增）')
    } else {
      await api.post('/products', productForm)
      ElMessage.success('商品新增成功')
    }
    productDialogVisible.value = false
    loadProducts()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

const handleDeleteProduct = (row) => {
  ElMessageBox.confirm(`确定要逻辑删除商品【${row.name}】吗？删除后 @TableLogic 将自动生效过滤`, '提示', { type: 'warning' })
    .then(async () => {
      await api.delete(`/products/${row.id}`)
      ElMessage.success('已逻辑删除')
      loadProducts()
    })
}

const handleProductPageChange = (page) => {
  productQuery.page = page
  loadProducts()
}

const handleOrderPageChange = (page) => {
  orderQuery.page = page
  loadOrders()
}

const handleUserPageChange = (page) => {
  userQuery.page = page
  loadUsers()
}

const showOrderDetail = (row) => {
  currentOrder.value = row
  orderDetailVisible.value = true
}

const handleShipOrder = (row) => {
  ElMessageBox.confirm(`确定对订单【${row.orderNo}】进行发货吗？`, '发货确认', { type: 'info' })
    .then(async () => {
      await api.put(`/orders/${row.id}/ship`)
      ElMessage.success('订单已发货')
      loadOrders()
    })
}

const handleCancelOrder = (row) => {
  ElMessageBox.confirm(`确定取消订单【${row.orderNo}】吗？`, '取消确认', { type: 'warning' })
    .then(async () => {
      await api.put(`/orders/${row.id}/cancel`)
      ElMessage.success('订单已取消')
      loadOrders()
    })
}

const openCategoryDialog = (row) => {
  if (row) {
    Object.assign(categoryForm, row)
  } else {
    Object.assign(categoryForm, { id: null, name: '', sort: 1 })
  }
  categoryDialogVisible.value = true
}

const handleSaveCategory = async () => {
  if (!categoryForm.name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  if (categoryForm.id) {
    await api.put(`/categories/${categoryForm.id}`, categoryForm)
  } else {
    await api.post('/categories', categoryForm)
  }
  categoryDialogVisible.value = false
  ElMessage.success('操作成功')
  loadCategories()
}

const handleDeleteCategory = (row) => {
  ElMessageBox.confirm(`确定删除分类【${row.name}】吗？`, '警告', { type: 'warning' })
    .then(async () => {
      await api.delete(`/categories/${row.id}`)
      ElMessage.success('分类已删除')
      loadCategories()
    })
}

const handleToggleUserStatus = async (row, val) => {
  const nextStatus = val ? 1 : 0
  await api.put(`/users/${row.id}/status?status=${nextStatus}`)
  row.status = nextStatus
  ElMessage.success(val ? '已启用账号' : '已禁用账号')
}

const calcPercentage = (val, total) => {
  if (!total || total === 0) return 0
  return Math.min(100, Math.round((val / total) * 100))
}

const getOrderStatusText = (s) => {
  const map = { 10: '待付款', 20: '待发货', 30: '已发货', 40: '已完成', 50: '已取消' }
  return map[s] || '未知'
}

const getOrderStatusTag = (s) => {
  const map = { 10: 'warning', 20: 'primary', 30: 'success', 40: 'info', 50: 'danger' }
  return map[s] || ''
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  loadDashboard()
  loadCategories()
})
</script>

<style scoped>
.layout-container { height: 100vh; }
.aside-menu { height: 100%; border-right: none; background-color: #1e222d; }
.logo { height: 60px; line-height: 60px; text-align: center; color: #fff; font-size: 16px; font-weight: bold; background: #181b24; letter-spacing: 1px; display: flex; align-items: center; justify-content: center; gap: 8px; }
.header-bar { background-color: #fff; box-shadow: 0 1px 4px rgba(0,21,41,.08); display: flex; justify-content: space-between; align-items: center; padding: 0 20px; height: 60px; }
.main-content { padding: 20px; overflow-y: auto; background-color: #f4f6f9; }
.card-stat { border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); }
.stat-title { font-size: 14px; color: #8c8c8c; margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: bold; color: #262626; }
.table-card { border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); }
.search-bar { margin-bottom: 18px; }
.product-thumb { width: 50px; height: 50px; border-radius: 4px; object-fit: cover; }
.badge-auto-include { background: #67c23a; color: white; padding: 2px 6px; border-radius: 4px; font-size: 12px; margin-left: 6px; }
</style>
