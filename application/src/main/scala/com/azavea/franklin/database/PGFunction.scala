package com.azavea.franklin.database

import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.refined.implicits._

object PGFunction {
  //def apply[T: Write](function: String, arg: String):
}

// async def dbfunc(pool: pool, func: str, arg: Union[str, Dict]):
//     """Wrap PLPGSQL Functions.
//     Keyword arguments:
//     pool -- the asyncpg pool to use to connect to the database
//     func -- the name of the PostgreSQL function to call
//     arg -- the argument to the PostgreSQL function as either a string
//     or a dict that will be converted into jsonb
//     """
//     try:
//         if isinstance(arg, str):
//             async with pool.acquire() as conn:
//                 q, p = render(
//                     f"""
//                         SELECT * FROM {func}(:item::text);
//                         """,
//                     item=arg,
//                 )
//                 return await conn.fetchval(q, *p)
//         else:
//             async with pool.acquire() as conn:
//                 q, p = render(
//                     f"""
//                         SELECT * FROM {func}(:item::text::jsonb);
//                         """,
//                     item=json.dumps(arg),
//                 )
//                 return await conn.fetchval(q, *p)
//     except exceptions.UniqueViolationError as e:
//         raise ConflictError from e
//     except exceptions.NoDataFoundError as e:
//         raise NotFoundError from e
//     except exceptions.NotNullViolationError as e:
//         raise DatabaseError from e
//     except exceptions.ForeignKeyViolationError as e:
//         raise ForeignKeyError from e
