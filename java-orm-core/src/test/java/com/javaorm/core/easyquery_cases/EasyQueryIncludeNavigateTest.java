package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.dto.BlogDTO;
import com.javaorm.core.easyquery_cases.dto.CompanyDTO;
import com.javaorm.core.easyquery_cases.dto.SysUserDTO;
import com.javaorm.core.easyquery_cases.entity.BlogEntity;
import com.javaorm.core.easyquery_cases.entity.Company;
import com.javaorm.core.easyquery_cases.entity.SysUser;
import com.javaorm.core.easyquery_cases.entity.Topic;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方关联导航与自动关联包含测试 (IncludeTest & SelectAutoIncludeTest)
 */
public class EasyQueryIncludeNavigateTest extends EasyQueryBaseTest {

    @Test
    public void testIncludeManyToOne() {
        BlogEntity b = orm.proxy(BlogEntity.class);

        // BlogEntity include Topic (ManyToOne)
        List<BlogEntity> blogs = orm.select(BlogEntity.class)
                .where(w -> w.in(b.getId(), java.util.Arrays.asList("b1", "b2", "b3")))
                .orderByAsc(b.getId())
                .include(b.getTopic())
                .toList();

        assertEquals(3, blogs.size());

        // b1 -> Topic 1
        BlogEntity b1 = blogs.get(0);
        assertNotNull(b1.getTopic());
        assertEquals("1", b1.getTopic().getId());
        assertEquals("Java ORM Guide", b1.getTopic().getTitle());

        // b2 -> Topic 1
        BlogEntity b2 = blogs.get(1);
        assertNotNull(b2.getTopic());
        assertEquals("1", b2.getTopic().getId());

        // b3 -> Topic 2
        BlogEntity b3 = blogs.get(2);
        assertNotNull(b3.getTopic());
        assertEquals("2", b3.getTopic().getId());
        assertEquals("EasyQuery Parity", b3.getTopic().getTitle());
    }

    @Test
    public void testIncludeOneToMany() {
        Company c = orm.proxy(Company.class);

        // Company include List<SysUser> users (OneToMany)
        List<Company> companies = orm.select(Company.class)
                .orderByAsc(c.getId())
                .includes(c.getUsers())
                .toList();

        assertEquals(2, companies.size());

        // c1 -> Alice, Bob
        Company c1 = companies.get(0);
        assertEquals("c1", c1.getId());
        assertNotNull(c1.getUsers());
        assertEquals(2, c1.getUsers().size());
        assertEquals("Alice", c1.getUsers().get(0).getName());
        assertEquals("Bob", c1.getUsers().get(1).getName());

        // c2 -> Charlie, David
        Company c2 = companies.get(1);
        assertEquals("c2", c2.getId());
        assertNotNull(c2.getUsers());
        assertEquals(2, c2.getUsers().size());
        assertEquals("Charlie", c2.getUsers().get(0).getName());
        assertEquals("David", c2.getUsers().get(1).getName());
    }

    @Test
    public void testIncludeOneToOne() {
        SysUser u = orm.proxy(SysUser.class);

        // SysUser include UserExtra (OneToOne)
        List<SysUser> users = orm.select(SysUser.class)
                .where(w -> w.in(u.getId(), java.util.Arrays.asList("u1", "u2")))
                .orderByAsc(u.getId())
                .include(u.getExtra())
                .toList();

        assertEquals(2, users.size());

        // u1 -> Beijing
        SysUser u1 = users.get(0);
        assertNotNull(u1.getExtra());
        assertEquals("Beijing", u1.getExtra().getCity());

        // u2 -> Shanghai
        SysUser u2 = users.get(1);
        assertNotNull(u2.getExtra());
        assertEquals("Shanghai", u2.getExtra().getCity());
    }

    @Test
    public void testSelectAutoIncludeCompanyDto() {
        Company c = orm.proxy(Company.class);

        // 一比一复刻 EasyQuery：查询 CompanyDTO，底层只查询 DTO 声明的列，并自动识别装配 users 列表
        List<CompanyDTO> companyDtos = orm.select(Company.class)
                .where(w -> w.eq(c.getId(), "c1"))
                .selectAutoInclude(CompanyDTO.class)
                .toList();

        assertEquals(1, companyDtos.size());
        CompanyDTO dto = companyDtos.get(0);
        assertEquals("c1", dto.getId());
        assertEquals("Antigravity Corp", dto.getName());

        assertNotNull("users 必须被自动包含填充", dto.getUsers());
        assertEquals(2, dto.getUsers().size());
        assertEquals("Alice", dto.getUsers().get(0).getName());
        assertEquals("Bob", dto.getUsers().get(1).getName());
    }

    @Test
    public void testSelectAutoIncludeBlogDto() {
        BlogEntity b = orm.proxy(BlogEntity.class);

        // 一比一复刻 EasyQuery：查询 BlogDTO，自动按嵌套 DTO 装配关联 TopicDTO
        List<BlogDTO> blogDtos = orm.select(BlogEntity.class)
                .where(w -> w.eq(b.getId(), "b1"))
                .selectAutoInclude(BlogDTO.class)
                .toList();

        assertEquals(1, blogDtos.size());
        BlogDTO dto = blogDtos.get(0);
        assertEquals("b1", dto.getId());
        assertEquals("Blog 1", dto.getTitle());

        assertNotNull("Topic 必须被自动填充", dto.getTopic());
        assertEquals("1", dto.getTopic().getId());
        assertEquals("Java ORM Guide", dto.getTopic().getTitle());
        assertEquals(Integer.valueOf(10), dto.getTopic().getStars());
    }

    @Test
    public void testSqlModeSelectAutoInclude() {
        Company c = orm.proxy(Company.class);

        // orm.sql() 模式下的 selectAutoInclude
        List<CompanyDTO> dtos = orm.sql()
                .select(Company.class)
                .where(w -> w.eq(c.getId(), "c2"))
                .selectAutoInclude(CompanyDTO.class)
                .toList();

        assertEquals(1, dtos.size());
        CompanyDTO c2 = dtos.get(0);
        assertEquals("Deepmind Lab", c2.getName());
        assertNotNull(c2.getUsers());
        assertEquals(2, c2.getUsers().size());
        assertEquals("Charlie", c2.getUsers().get(0).getName());
        assertEquals("David", c2.getUsers().get(1).getName());
    }
}
