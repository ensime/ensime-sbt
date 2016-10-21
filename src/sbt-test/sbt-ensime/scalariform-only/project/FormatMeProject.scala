object FormatMe { val number = 10
List(number) match { case head :: Nil if head % 2 == 0 => "number is even"
case head :: Nil => "number is not even"
case Nil => "List is empty" }}

class Person(name: String,
age: Int = 24,
birthdate: String,
astrologicalSign: String = "libra",
shoeSize: Int,
favoriteColor: String  
)
